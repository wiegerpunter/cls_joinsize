package org.synopses.ChowMin;

import net.sf.jsqlparser.schema.Table;
import org.dataset.JoinDataset;
import org.dataset.SynopsisQuery;
import org.duckdb.DuckDBConnection;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import static org.synopses.ChowMin.FilteredTable.decodeIdx;
import static org.synopses.ChowMin.FilteredTable.getIdx;

public class CMJoin {
    HashMap<String, ChowMinAllPairs> cmj;
    HashMap<String, String[]> attrMap;
    int depth;
    int width;
    public String setting = "CMJoin";
    JoinDataset jd;

    public CMJoin() {
        cmj = new HashMap<>();
        attrMap = new HashMap<>();
    }

    public long initialize(JoinDataset jd, HashMap<String, String[]> attrMap, int ram, int depth, int width, DuckDBConnection con) {
        this.depth = depth;
        this.width = width;
        this.jd = jd;
        HashMap<String, Integer> numColumnsPerTable = jd.numColumnsPerTable;
        long start = System.currentTimeMillis();
        for (String t : jd.tableNames) {
            ChowMinAllPairs syn = new ChowMinAllPairs(t, attrMap.get(t), jd.globalColToJoinKey, numColumnsPerTable.get(t), depth, width, ram);
            int numColumns = jd.numColumnsPerTable.get(t);
            String dataQuery = "SELECT * FROM " + t;
            try (Statement stmt = con.createStatement();
                 ResultSet rs = stmt.executeQuery(dataQuery)
            ) {
                while (rs.next()) {
                    String[] rec = new String[numColumns];
                    for (int col = 1; col <= numColumns; col++) {
                        rec[col - 1] = rs.getString(col);
                    }
                    syn.addStringArr(rec);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            cmj.put(t, syn);
        }
        long end = System.currentTimeMillis();
        long time=end-start;

        for (ChowMinAllPairs syn : cmj.values()) {
            syn.setMIs(syn.estimateMIs(), true);
        }
        return time;
    }
    public record QueryEst(double estimate, Map<String, FilteredTable> map) {

    }

    public QueryEst query(SynopsisQuery synQuery) {
        // 0. for each repetition in [d]
        // 1. select tables/chowmins for query
        // 2. apply predicates selecting for the eq join key something that it has to multiply with
        // 3. join size is estimated by inference procedure over the chowmin hash codomains per eq join key and multiplying with the local predicate tensors.
        double[] estimates = new double[depth];
        Map<Double, Map<String, FilteredTable>> estToFilteredMap = new HashMap<>();
        for (int j = 0; j < depth; j++) {
            Map<String, FilteredTable> filtered = filterTables(j, synQuery); // applying local predicates.
            estimates[j] = estimateJoinSize(synQuery, filtered, width);
            estToFilteredMap.put(estimates[j], filtered);
        }
        Arrays.sort(estimates);
        Map<String, FilteredTable> filteredEstimate = estToFilteredMap.get(estimates[estimates.length/2]);
        return new QueryEst(estimates[estimates.length/2], filteredEstimate);
    }
    private Map<String, FilteredTable> filterTables(int j, SynopsisQuery query) {
        HashMap<String, FilteredTable> map = new HashMap<>();
        for (Table t: query.tables) {
            map.put(t.getAlias().getName(), new FilteredTable(j, t, query, cmj.get(t.getName())));
        }
        return map;
    }

    private double estimateJoinSize(SynopsisQuery query, Map<String, FilteredTable> filtered, int w) {
        // All unique join keys participating in the query
        double estimate = 0.0;
        // Enumerate all possible codomains of the equivalent join keys in this query
        int numJoinKeys = query.numJoinKeys;
        int totalBuckets = (int) Math.pow(w, numJoinKeys);
        int[][] tableBuckets = new int[filtered.size()][];
        int t=0;
        for (FilteredTable ft:filtered.values()) {
            tableBuckets[t] = new int[ft.unqColumns.size()];
            t++;
        }
        for (int idx = 0; idx < totalBuckets; idx++) {
            int[] bucketAssignment = decodeIdx(idx, numJoinKeys, w);
            double[][] tableCounts = new double[filtered.size()][];
            int tloop=0;
            for (FilteredTable ft : filtered.values()) {
                for (int i = 0; i < ft.unqColumns.size(); i++) {
                    String globalJK = jd.globalColToJoinKey.get(ft.unqColumns.get(i));
                    int jk = query.jkToBucket.get(globalJK);
                    tableBuckets[tloop][i] = bucketAssignment[jk];
                }
                int viewIdx = getIdx(tableBuckets[tloop], w);
                double[] count = ft.view[viewIdx];
                tableCounts[tloop] = count;
//                if (ft.isPrimaryKeyJoinKey()) {
//                    pkRatios[jk]  = 1/count;
//                    count = count > 0 ? 1 : 0;
//                } else {
//
//                }
//                product *= count;
                tloop++;
            }
            double[][] pkCount =
                    new double[numJoinKeys][2];
            for (int i = 0; i < pkCount.length; i++) {
                pkCount[i] = new double[]{-1,-1};
            }

            tloop = 0;

            for (FilteredTable ft : filtered.values()) {

                if (ft.isPrimaryKeyTable) {
                    SynopsisQuery.ColumnKey id = getPK(ft.unqColumns);
                    int idIdx = getPKIdx(id, ft.unqColumns);
                    String globalJK =
                            jd.globalColToJoinKey.get(id);

                    int jk =
                            query.jkToBucket.get(globalJK);

                    pkCount[jk][0] = getMarginal(ft, idIdx, bucketAssignment[jk], w);//tableCounts[tloop][0];
                    if (ft.pkHasPred) {
                        if (tableCounts[tloop][1] > 1.0) {
                            throw new RuntimeException("help");
                        }
                        pkCount[jk][1] = Math.min(tableCounts[tloop][1], 1.0);
                    } else {
                        pkCount[jk][1] = 0.0;
                    }
                }

                tloop++;
            }

            tloop = 0;
            double product = 1.0;
            for (FilteredTable ft : filtered.values()) {

                double count = tableCounts[tloop][0];

                if (ft.isPrimaryKeyTable) {
                    SynopsisQuery.ColumnKey pk = getPK(ft.unqColumns);
                    for (int i = 0; i < ft.unqColumns.size(); i++) {
                        if (ft.unqColumns.get(i).equals(pk)) {
                            count = count;
                        } else {
                            String globalJK =
                                    jd.globalColToJoinKey.get(
                                            ft.unqColumns.get(i));

                            int jk =
                                    query.jkToBucket.get(globalJK);

                            if (pkCount[jk][1] > 0.0) {
                                count *= pkCount[jk][1];
                            }
                            count = count / Math.max(1.0, pkCount[jk][0]);//Math.pow(count, 0.8);//1.0 / fkCountPerJK[jk]);

                        }
                    }

                } else {

                    /*
                     * If this table is an FK for a PK/FK join,
                     * divide by the number of PK values in the
                     * corresponding PK bucket.
                     */
                    for (int i = 0;
                         i < ft.unqColumns.size();
                         i++) {

                        String globalJK =
                                jd.globalColToJoinKey.get(
                                        ft.unqColumns.get(i));

                        int jk =
                                query.jkToBucket.get(globalJK);

                        if (pkCount[jk][1] > 0.0) {
                            count *= pkCount[jk][1];
                        }
                        count = count / Math.max(1.0, pkCount[jk][0]);//Math.pow(count, 0.8);//1.0 / fkCountPerJK[jk]);
                    }
                }

                product *= count;
                tloop++;
            }

            estimate += product;
        }
        return estimate;
    }

    private int getPKIdx(SynopsisQuery.ColumnKey id, List<SynopsisQuery.ColumnKey> unqColumns) {
        for (int i = 0; i < unqColumns.size(); i++) {
            if (unqColumns.get(i).equals(id)) {
                return i;
            }
        }
        throw new RuntimeException("Id of primary key not found");
    }

    private double getMarginal(
            FilteredTable ft,
            int columnIndex,
            int bucket,
            int w) {

        return ft.pkView[bucket];
//        int k = ft.unqColumns.size();
//
//        double sum = 0.0;
//
//        for (int idx = 0; idx < Math.pow(w, k); idx++) {
//            int[] assignment = decodeIdx(idx, k, w);
//
//            if (assignment[columnIndex] != bucket) {
//                continue;
//            }
//
//            int viewIdx = getIdx(assignment, w);
//            sum += ft.view[viewIdx][0];
//        }
//
//        return sum;
    }

    private SynopsisQuery.ColumnKey getPK(List<SynopsisQuery.ColumnKey> unqColumns) {
        for (SynopsisQuery.ColumnKey c : unqColumns) {
            if (c.column().equals("Id") || c.column().equals("id")) {
                return c;
            }
        }
        throw new RuntimeException("No PK column found in " + unqColumns.toString());
    }

    public int getDepth() {
        return depth;
    }

    public int getWidth() {
        return width;
    }

    public int getMemory() {
        int ram = 0;
        for (ChowMinAllPairs sketch : cmj.values()) {
            for (TWODCM subsketch: sketch.sketches) {
                ram+= subsketch.memory();
            }
        }
        return ram;
    }

    public int getDatasetMemory() {
        int ram =0;
        for (ChowMinAllPairs sketch : cmj.values()) {
            ram = sketch.getN() * sketch.getNumColumns()*4;
        }
        return ram;

    }

}
