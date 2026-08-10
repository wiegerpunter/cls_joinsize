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

    public double query(SynopsisQuery synQuery) {
        // 0. for each repetition in [d]
        // 1. select tables/chowmins for query
        // 2. apply predicates selecting for the eq join key something that it has to multiply with
        // 3. join size is estimated by inference procedure over the chowmin hash codomains per eq join key and multiplying with the local predicate tensors.
        double[] estimates = new double[depth];
        for (int j = 0; j < depth; j++) {
            Map<String, FilteredTable> filtered = filterTables(j, synQuery); // applying local predicates.
            estimates[j] = estimateJoinSize(synQuery, filtered, width);
        }
        Arrays.sort(estimates);
        return estimates[0];
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
            double product = 1;
            int tloop=0;
            for (FilteredTable ft : filtered.values()) {
                for (int i = 0; i < ft.unqColumns.size(); i++) {
                    tableBuckets[tloop][i] = bucketAssignment[query.jkToBucket.get(jd.globalColToJoinKey.get(ft.unqColumns.get(i)))];
                }
                int viewIdx = getIdx(tableBuckets[tloop], w);
                double count = ft.view[viewIdx];
                if (ft.isPrimaryKeyJoinKey()) {
                    count = count > 0 ? 1 : 0;
                }
                product *= count;
                tloop++;
            }
            estimate += product;
        }
        return estimate;
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
