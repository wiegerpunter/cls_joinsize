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
    public HashMap<String, HashMap<String, Integer>> widthMap;
    HashMap<String, Integer> joinKeyWidthMap;
    public String setting = "CMJoin";
    JoinDataset jd;

    public CMJoin() {
        cmj = new HashMap<>();
        attrMap = new HashMap<>();
    }

    public long initialize(JoinDataset jd, HashMap<String, String[]> attrMap, int ram, int depth, HashMap<String, HashMap<String, Integer>> widthMap, HashMap<String, Integer> joinKeyWidthMap, boolean sepHHs, HashMap<String, HashMap<String, List<String>>> hhValuesMaps, DuckDBConnection con) {
        this.depth = depth;
        this.widthMap = widthMap;
        this.joinKeyWidthMap = joinKeyWidthMap;
        this.jd = jd;
        HashMap<String, Integer> numColumnsPerTable = jd.numColumnsPerTable;
        long start = System.currentTimeMillis();
        for (String t : jd.tableNames) {
            HashMap<String, List<String>> hhValuesMapsTable = null;
            if (sepHHs)
                hhValuesMapsTable = hhValuesMaps.get(t);
            ChowMinAllPairs syn = new ChowMinAllPairs(t, attrMap.get(t), jd.globalColToJoinKey, numColumnsPerTable.get(t), depth, widthMap.get(t), ram, sepHHs, hhValuesMapsTable);
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
            estimates[j] = estimateJoinSize(synQuery, filtered);
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

    private int[] getTableJoinKeyWidths(FilteredTable ft) {
        int[] widths = new int[ft.unqColumns.size()];

        for (int i = 0; i < ft.unqColumns.size(); i++) {
            widths[i] = widthMap.get(ft.t.getName()).get(ft.unqColumns.get(i).column());
        }

        return widths;
    }

//    private double estimateJoinSize(SynopsisQuery query, Map<String, FilteredTable> filtered) {
//        // All unique join keys participating in the query
//        double estimate = 0.0;
//        // Enumerate all possible codomains of the equivalent join keys in this query
//        int numJoinKeys = query.numJoinKeys;
//        List<String> joinKeys = setJoinKeyList(query);
//        int[] querySpecificJoinKeyWidthMap = getQuerySpecificJoinKeyWidthMap(joinKeys);
//        int totalBuckets = getTotalGlobalJoinAssignments(querySpecificJoinKeyWidthMap);
//        int[][] tableBuckets = new int[filtered.size()][];
//        int t=0;
//        for (FilteredTable ft:filtered.values()) {
//            tableBuckets[t] = new int[ft.unqColumns.size()];
//            t++;
//        }
//        for (int idx = 0; idx < totalBuckets; idx++) {
//            int[] bucketAssignment = decodeIdx(idx, querySpecificJoinKeyWidthMap);
//            double[][] tableCounts = new double[filtered.size()][];
//            int tloop=0;
//            for (FilteredTable ft : filtered.values()) {
//                for (int i = 0; i < ft.unqColumns.size(); i++) {
//                    String globalJK = jd.globalColToJoinKey.get(ft.unqColumns.get(i));
//                    int jk = query.jkToBucket.get(globalJK);
//                    tableBuckets[tloop][i] = bucketAssignment[jk];
//                }
//                int[] tableJoinKeyWidths = getTableJoinKeyWidths(ft);
//                int viewIdx = getIdx(tableBuckets[tloop], tableJoinKeyWidths);
//                double[] count = ft.view[viewIdx];
//                tableCounts[tloop] = count;
////                if (ft.isPrimaryKeyJoinKey()) {
////                    pkRatios[jk]  = 1/count;
////                    count = count > 0 ? 1 : 0;
////                } else {
////
////                }
////                product *= count;
//                tloop++;
//            }
//            double[][] pkCount = new double[numJoinKeys][2];
//            for (int i = 0; i < pkCount.length; i++) {
//                pkCount[i] = new double[]{-1,-1};
//            }
//
//            tloop = 0;
//
//            for (FilteredTable ft : filtered.values()) {
//
//                if (ft.isPrimaryKeyTable) {
//                    SynopsisQuery.ColumnKey id = getPK(ft.t, ft.unqColumns);
////                    int idIdx = getPKIdx(id, ft.unqColumns);
//                    String globalJK = jd.globalColToJoinKey.get(id);
//
//                    int jk = query.jkToBucket.get(globalJK);
//
//                    pkCount[jk][0] = getMarginal(ft, bucketAssignment[jk]);//tableCounts[tloop][0];
//                    if (ft.pkHasPred) {
//                        if (tableCounts[tloop][1] > 1.0) {
//                            throw new RuntimeException("help");
//                        }
//                        pkCount[jk][1] = Math.min(tableCounts[tloop][1], 1.0);
//                    } else {
//                        pkCount[jk][1] = 0.0;
//                    }
//                }
//
//                tloop++;
//            }
//
//            tloop = 0;
//            double product = 1.0;
//            for (FilteredTable ft : filtered.values()) {
//
//                double count = tableCounts[tloop][0];
//
//                if (ft.isPrimaryKeyTable) {
//                    SynopsisQuery.ColumnKey pk = getPK(ft.t, ft.unqColumns);
//                    for (int i = 0; i < ft.unqColumns.size(); i++) {
//                        if (ft.unqColumns.get(i).equals(pk)) {
//                            count = count;
//                        } else {
//                            String globalJK =
//                                    jd.globalColToJoinKey.get(
//                                            ft.unqColumns.get(i));
//
//                            int jk =
//                                    query.jkToBucket.get(globalJK);
//
//                            if (pkCount[jk][1] > 0.0) {
//                                count *= pkCount[jk][1];
//                            }
//                            count = count / Math.max(1.0, pkCount[jk][0]);//Math.pow(count, 0.8);//1.0 / fkCountPerJK[jk]);
//
//                        }
//                    }
//
//                } else {
//
//                    /*
//                     * If this table is an FK for a PK/FK join,
//                     * divide by the number of PK values in the
//                     * corresponding PK bucket.
//                     */
//                    for (int i = 0;
//                         i < ft.unqColumns.size();
//                         i++) {
//
//                        String globalJK =
//                                jd.globalColToJoinKey.get(
//                                        ft.unqColumns.get(i));
//
//                        int jk = query.jkToBucket.get(globalJK);
//
//                        if (pkCount[jk][1] > 0.0) {
//                            count =count;//*= pkCount[jk][1];
//                        }
//                        count = count / Math.max(1.0, pkCount[jk][0]);//Math.pow(count, 0.8);//1.0 / fkCountPerJK[jk]);
//                    }
//                }
//
//                product *= count;
//                tloop++;
//            }
//
//            estimate += product;
//        }
//        return estimate;
//    }

    private double estimateJoinSize(
            SynopsisQuery query,
            Map<String, FilteredTable> filtered) {

        double estimate = 0.0;
        int numJoinKeys = query.numJoinKeys;

        // All unique join keys participating in the query.
        List<String> joinKeys = setJoinKeyList(query);

        // Width of each join-key dimension in the query-specific
        // hashed join-key assignment space.
        int[] queryJoinKeyWidths =
                getQuerySpecificJoinKeyWidthMap(joinKeys);

        int totalBuckets =
                getTotalGlobalJoinAssignments(queryJoinKeyWidths);

        /*
         * tableBuckets[t][i] is the bucket assignment for the
         * i-th join key of table t.
         */
        int[][] tableBuckets = new int[filtered.size()][];

        int tableIdx = 0;
        for (FilteredTable ft : filtered.values()) {
            tableBuckets[tableIdx] =
                    new int[ft.unqColumns.size()];
            tableIdx++;
        }

        /*
         * Enumerate the global hashed join-key assignment space.
         */
        for (int idx = 0; idx < totalBuckets; idx++) {

            int[] bucketAssignment =
                    decodeIdx(idx, queryJoinKeyWidths);

            /*
             * ------------------------------------------------------------
             * Step 1: obtain the local frequency estimate for every table.
             * ------------------------------------------------------------
             */
            double[][] tableCounts =
                    new double[filtered.size()][];

            tableIdx = 0;

            for (FilteredTable ft : filtered.values()) {

                /*
                 * Project the global join-key assignment onto the
                 * join keys present in this table.
                 */
                for (int i = 0;
                     i < ft.unqColumns.size();
                     i++) {

                    String globalJK =
                            jd.globalColToJoinKey.get(
                                    ft.unqColumns.get(i));

                    int jk =
                            query.jkToBucket.get(globalJK);

                    tableBuckets[tableIdx][i] =
                            bucketAssignment[jk];
                }

                int[] tableJoinKeyWidths =
                        getTableJoinKeyWidths(ft);

                int viewIdx =
                        getIdx(
                                tableBuckets[tableIdx],
                                tableJoinKeyWidths);

                tableCounts[tableIdx] =
                        ft.view[viewIdx];

                tableIdx++;
            }


            /*
             * ------------------------------------------------------------
             * Step 2: collect PK information for every join key.
             *
             * pkCount[jk][0] = total PK values in the bucket
             * pkCount[jk][1] = selected PK values in the bucket
             *
             * For a PK without a predicate:
             *
             *     selectedPK == totalPK
             *
             * and therefore the FK correction becomes 1.
             * ------------------------------------------------------------
             */
            double[][] pkCount =
                    new double[numJoinKeys][2];

            for (int i = 0;
                 i < numJoinKeys;
                 i++) {

                pkCount[i][0] = -1.0;
                pkCount[i][1] = -1.0;
            }

            tableIdx = 0;

            for (FilteredTable ft : filtered.values()) {

                for (int i = 0;
                     i < ft.unqColumns.size();
                     i++) {

                    String globalJK =
                            jd.globalColToJoinKey.get(
                                    ft.unqColumns.get(i));

                    int jk =
                            query.jkToBucket.get(globalJK);

                    /*
                     * Only collect PK information when this particular
                     * join key is a PK in this relation.
                     */
                    if (!isPkForJoinKey(ft, globalJK)) {
                        continue;
                    }

                    /*
                     * The PK marginal must be UNFILTERED.
                     *
                     * This is important: the denominator represents
                     * all PK values in the bucket.
                     */
                    double totalPK =
                            getMarginal(
                                    ft,
                                    bucketAssignment[jk]);

                    /*
                     * If the PK has a predicate, tableCounts[][0]
                     * represents the selected PK count in this bucket.
                     *
                     * Otherwise every PK survives.
                     */
                    double selectedPK;

                    if (ft.pkHasPred) {
                        selectedPK =
                                tableCounts[tableIdx][1];
                    } else {
                        selectedPK = totalPK;
                    }

                    pkCount[jk][0] = totalPK;
                    pkCount[jk][1] = selectedPK;
                }

                tableIdx++;
            }


            /*
             * ------------------------------------------------------------
             * Step 3: combine the local estimates.
             *
             * PK side:
             *     use its local count directly.
             *
             * FK side:
             *     multiply by
             *
             *       selected PKs / total PKs
             *
             *     for the corresponding join key.
             * ------------------------------------------------------------
             */
            double product = 1.0;

            tableIdx = 0;

            for (FilteredTable ft : filtered.values()) {

                double count =
                        tableCounts[tableIdx][0];
                if (tableCounts[tableIdx].length>1) {
                    count = tableCounts[tableIdx][1];
                }
                for (int i = 0;
                     i < ft.unqColumns.size();
                     i++) {

                    String globalJK =
                            jd.globalColToJoinKey.get(
                                    ft.unqColumns.get(i));

                    int jk =
                            query.jkToBucket.get(globalJK);

                    /*
                     * If this table is the PK side of this particular
                     * relationship, its local frequency already
                     * represents the number of PK values/tuples in
                     * this bucket satisfying its local predicates.
                     *
                     * Nothing needs to be done.
                     */
                    if (isPkForJoinKey(ft, globalJK)) {
                        continue;
                    }

                    /*
                     * Otherwise this table is on the FK side.
                     *
                     * Convert the number of FK tuples in the bucket
                     * into the estimated number that reference PK
                     * values surviving the PK-side predicate.
                     */
                    double totalPK =
                            pkCount[jk][0];

                    if (totalPK > 0.0) {
                        count *= 1.0 / totalPK;
                    }
                }

                product *= count;
                tableIdx++;
            }

            if (product>0.0) {
                estimate += product;
            }
        }

        return estimate;
    }

    private boolean isPkForJoinKey(FilteredTable ft, String globalJK) {
        SynopsisQuery.ColumnKey ck = getPK(ft.t, ft.unqColumns);
        if (ck.column().isEmpty()) {
            return false;
        }
        String column = jd.globalJoinKeyToTableColumn.get(globalJK).get(ft.t.getName());
        return Objects.equals(ck.column(), column);
    }

    private List<String> setJoinKeyList(SynopsisQuery query) {
        return new ArrayList<>(query.globalJoinKeysInQuerySet);
    }

    private int getTotalGlobalJoinAssignments(int[] querySpecificJoinKeyWidthMap) {
        double product = 1.0;
        for (int w : querySpecificJoinKeyWidthMap) {
            product *= w;
        }
        return (int) product;
    }

    private int[] getQuerySpecificJoinKeyWidthMap(List<String> joinKeys) {
        int[] querySpecificJoinKeyWidthMap = new int[joinKeys.size()];
        for (int i = 0; i< joinKeys.size(); i++){
            querySpecificJoinKeyWidthMap[i] = joinKeyWidthMap.get(joinKeys.get(i));
        }
        return querySpecificJoinKeyWidthMap;
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
            int bucket) {

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

    private SynopsisQuery.ColumnKey getPK(Table t, List<SynopsisQuery.ColumnKey> unqColumns) {
        for (SynopsisQuery.ColumnKey c : unqColumns) {
            if (c.column().equals("Id") || c.column().equals("id")) {
                return c;
            }
            SynopsisQuery.ColumnKey k = isPKTPCH(t, c);
            if (!k.column().isEmpty()) {
                return k;
            }
        }
        return new SynopsisQuery.ColumnKey(t.getName(), "");
//        throw new RuntimeException("No PK column found in " + unqColumns.toString());
    }

    private static SynopsisQuery.ColumnKey isPKTPCH(Table t, SynopsisQuery.ColumnKey ck) {
        String tname = t.getAlias().getName();
        String c = ck.column();
        if (tname.equals("c")) {
            if (c.equals("c_custkey")) {
                return ck;
            }
        }
        if (tname.equals("o")) {
            if (c.equals("o_orderkey")) {
                return ck;
            }
        }
        if (tname.equals("n")) {
            if (c.equals("n_nationkey")) {
                return ck;
            }
        }
        if (tname.equals("r")) {
            if (c.equals("r_regionkey")) {
                return ck;
            }
        }
        if (tname.equals("s")) {
            if (c.equals("s_suppkey")) {
                return ck;
            }
        }
        if (tname.equals("p")) {
            if (c.equals("p_partkey")) {
                return ck;
            }
        }
        return new SynopsisQuery.ColumnKey(tname, "");
    }

    public int getDepth() {
        return depth;
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
            ram += sketch.getN() * sketch.getNumColumns()*4;
        }
        return ram;

    }

}
