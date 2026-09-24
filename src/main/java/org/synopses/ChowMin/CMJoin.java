package org.synopses.ChowMin;

import net.sf.jsqlparser.schema.Table;
import org.dataset.JoinDataset;
import org.dataset.SynopsisQuery;
import org.duckdb.DuckDBConnection;
import org.experiments.RunJoinExperiment;

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
    int[] globalWidths;
    public String setting = "CMJoin";
    JoinDataset jd;

    public CMJoin(JoinDataset jd, HashMap<String, String[]> attrMap, int ram, int depth,
                  HashMap<String, HashMap<String, Integer>> widthMap,
                  HashMap<String, HashMap<String, Boolean>> exactHashable,
                  HashMap<String, HashMap<String, HashMap<String, Integer>>> exactHashMap,
                  HashMap<String, Integer> joinKeyWidthMap, boolean sepHHs,
                  HashMap<String, HashMap<String, List<String>>> hhValuesMaps,
                  int seed) {
        cmj = new HashMap<>();
        this.attrMap = attrMap;
        this.depth = depth;
        this.widthMap = widthMap;
        this.joinKeyWidthMap = joinKeyWidthMap;
        this.globalWidths = new int[joinKeyWidthMap.size()];
        for (String jk : joinKeyWidthMap.keySet()) {
            int j = Integer.parseInt(jk.substring(1));
            globalWidths[j] = joinKeyWidthMap.get(jk);
        }
        this.jd = jd;
        HashMap<String, Integer> numColumnsPerTable = jd.numColumnsPerTable;
        HashMap<String, List<String>> hhValuesMapsTable = null;
        for (String t : jd.tableNames) {
            if (sepHHs)
                hhValuesMapsTable = hhValuesMaps.get(t);
            ChowMinAllPairs syn = new ChowMinAllPairs(t, attrMap.get(t), jd.globalColToJoinKey, numColumnsPerTable.get(t),
                    depth, widthMap.get(t), exactHashable.getOrDefault(t, new HashMap<>()),
                    exactHashMap.getOrDefault(t, new HashMap<>()), ram, sepHHs, hhValuesMapsTable, seed);

            cmj.put(t, syn);
        }
    }

    public long initialize(DuckDBConnection con) {

        long start = System.currentTimeMillis();
        for (String t : jd.tableNames) {
            ChowMinAllPairs syn = cmj.get(t);
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

        // All unique join keys participating in the query.
        List<String> joinKeys = new ArrayList<>(synQuery.globalJoinKeysInQuerySet);

        Map<Double, Map<String, FilteredTable>> estToFilteredMap = new HashMap<>();
        for (int j = 0; j < depth; j++) {
            Map<String, FilteredTable> filtered = filterTables(j, synQuery); // applying local predicates.
            List<FilteredTable.Factor> factors = buildFactors(filtered, globalWidths, synQuery.bucketToJK);
            estimates[j] = variableElimination(factors, globalWidths.length, globalWidths);
//            double oldEst =  estimateJoinSize(synQuery, filtered, joinKeys);
//            if (Math.abs(estimates[j] - oldEst) >=1000) {
//               System.out.println("Var EL not equal to old estimate");
//            }
            estToFilteredMap.put(estimates[j], filtered);
        }
        Arrays.sort(estimates);
        Map<String, FilteredTable> filteredEstimate = estToFilteredMap.get(estimates[0]);
        return new QueryEst(estimates[0], filteredEstimate);
//        return new QueryEst(estimates[estimates.length/2], filteredEstimate);
    }
    private Map<String, FilteredTable> filterTables(int j, SynopsisQuery query) {
        HashMap<String, FilteredTable> map = new HashMap<>();
        for (Table t: query.tables) {
            map.put(t.getAlias().getName(), new FilteredTable(j, t, query, cmj.get(t.getName()), jd.globalColToJoinKey));
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
            Map<String, FilteredTable> filtered, List<String> joinKeys) {

        double estimate = 0.0;
        int numJoinKeys = query.numJoinKeys;


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
        int[] bucketAssignment = new int[numJoinKeys];

        /*
         * Enumerate the global hashed join-key assignment space.
         */
        double[][] tableCounts =
                new double[filtered.size()][];
        double[][] pkCount =
                new double[numJoinKeys][2];
        for (int idx = 0; idx < totalBuckets; idx++) {
//            for (int k = numJoinKeys - 1; k >= 0; k--) {
//                if (++bucketAssignment[k] < queryJoinKeyWidths[k]) {
//                    break;
//                }
//                bucketAssignment[k] = 0;
//            }
            bucketAssignment =
                    decodeIdx(idx, queryJoinKeyWidths);

            /*
             * ------------------------------------------------------------
             * Step 1: obtain the local frequency estimate for every table.
             * ------------------------------------------------------------
             */


            tableIdx = 0;

            for (FilteredTable ft : filtered.values()) {

                /*
                 * Project the global join-key assignment onto the
                 * join keys present in this table.
                 */
                for (int i = 0;
                     i < ft.unqColumns.size();
                     i++) {

//                    String globalJK =
//                            jd.globalColToJoinKey.get(
//                                    ft.unqColumns.get(i));

                    int jk = ft.globalJoinKeyIdx[i];
//                            query.jkToBucket.get(globalJK);

                    tableBuckets[tableIdx][i] =
                            bucketAssignment[jk];
                }
                int viewIdx =
                        getIdx(
                                tableBuckets[tableIdx],
                                ft.joinKeyWidths);

                tableCounts[tableIdx] = ft.view[viewIdx];


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

//                    String globalJK =
//                            jd.globalColToJoinKey.get(
//                                    ft.unqColumns.get(i));

//                    int jk =
//                            query.jkToBucket.get(globalJK);
                    int jk = ft.globalJoinKeyIdx[i];
                    /*
                     * Only collect PK information when this particular
                     * join key is a PK in this relation.
                     */
                    if (!ft.pkJoinKey[i]){// isPkForJoinKey(ft, globalJK)) {
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
                if (count==0){
                    product = 0;
                    continue;
                }
                for (int i = 0;
                     i < ft.unqColumns.size();
                     i++) {

//                    String globalJK =
//                            jd.globalColToJoinKey.get(
//                                    ft.unqColumns.get(i));

                    int jk = ft.globalJoinKeyIdx[i];
//                            query.jkToBucket.get(globalJK);

                    /*
                     * If this table is the PK side of this particular
                     * relationship, its local frequency already
                     * represents the number of PK values/tuples in
                     * this bucket satisfying its local predicates.
                     *
                     * Nothing needs to be done.
                     */
                    if (ft.pkJoinKey[i]){//isPkForJoinKey(ft, globalJK)) {
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

    private List<FilteredTable.Factor> buildFactors(
            Map<String, FilteredTable> filtered,
            int[] globalWidths,
            HashMap<Integer, Integer> bucketToJK) {

        List<FilteredTable.Factor> factors =
                new ArrayList<>();

        /*
         * First add the table factors.
         */
        for (FilteredTable ft : filtered.values()) {

            factors.add(
                    makeTableFactor(
                            ft,
                            globalWidths,
                            bucketToJK));
        }

        for (FilteredTable ft : filtered.values()) {

            for (int i = 0;
                 i < ft.globalJoinKeyIdx.length;
                 i++) {

                /*
                 * Nothing to correct on the PK side.
                 */
                if (ft.pkJoinKey[i]) {
                    continue;
                }

                int jk =
                        ft.globalJoinKeyIdx[i];

                /*
                 * Find the PK relation for this global join key.
                 */
                FilteredTable pkTable = null;

                for (FilteredTable candidate : filtered.values()) {

                    for (int k = 0;
                         k < candidate.globalJoinKeyIdx.length;
                         k++) {

                        if (candidate.globalJoinKeyIdx[k] == jk
                                && candidate.pkJoinKey[k]) {

                            pkTable = candidate;
                            break;
                        }
                    }

                    if (pkTable != null) {
                        break;
                    }
                }

                if (pkTable == null) {
                    continue;
//                    throw new IllegalStateException(
//                            "No PK side found for join key " + jk);
                }

                int width =
                        globalWidths[bucketToJK.get(jk)];

                factors.add(
                        makeInversePKFactor(
                                pkTable,
                                bucketToJK.get(jk),
                                width));
            }
        }


        return factors;
    }

    private FilteredTable.Factor makeTableFactor(
            FilteredTable ft,
            int[] globalWidths,
            HashMap<Integer, Integer> bucketToJK) {

        int[] vars = ft.globalJoinKeyIdx.clone();
        for (int i = 0; i < vars.length; i++) {
            vars[i] = bucketToJK.get(vars[i]);
        }
        int[] widths = ft.joinKeyWidths.clone();

        double[] values = new double[ft.view.length];

        for (int i = 0; i < ft.view.length; i++) {
            /*
             * If the table has a PK predicate, view[][1] is the
             * predicate-filtered count. Otherwise view[][0] is the
             * appropriate local count.
             */
            if (ft.pkHasPred && ft.view[i].length > 1) {
                values[i] = ft.view[i][1];
            } else {
                values[i] = ft.view[i][0];
            }
        }

        return new FilteredTable.Factor(vars, widths, values);
    }

    private FilteredTable.Factor makeCountFactor(
            FilteredTable ft,
            int[] globalWidths) {

        int k = ft.globalJoinKeyIdx.length;

        int[] vars = ft.globalJoinKeyIdx.clone();
        int[] widths = ft.joinKeyWidths.clone();

        double[] values = new double[ft.view.length];

        for (int i = 0; i < ft.view.length; i++) {
            values[i] = ft.view[i][0];
        }

        return new FilteredTable.Factor(vars, widths, values);
    }

    private FilteredTable.Factor makeSelectedFactor(
            FilteredTable ft) {

        int k = ft.globalJoinKeyIdx.length;

        int[] vars = ft.globalJoinKeyIdx.clone();
        int[] widths = ft.joinKeyWidths.clone();

        double[] values = new double[ft.view.length];

        for (int i = 0; i < ft.view.length; i++) {
            values[i] = ft.view[i][1];
        }

        return new FilteredTable.Factor(vars, widths, values);
    }


    private FilteredTable.Factor makePKFactor(
            FilteredTable ft,
            int globalJK,
            int width) {

        double[] values = new double[width];

        for (int b = 0; b < width; b++) {
            values[b] = getMarginal(ft, b);
        }

        return new FilteredTable.Factor(
                new int[]{globalJK},
                new int[]{width},
                values
        );
    }

    private FilteredTable.Factor makeInversePKFactor(
            FilteredTable ft,
            int globalJK,
            int width) {

        double[] values = new double[width];

        for (int b = 0; b < width; b++) {
            double x = getMarginal(ft, b);

            values[b] = x > 0.0 ? 1.0 / x : 0.0;
        }

        return new FilteredTable.Factor(
                new int[]{globalJK},
                new int[]{width},
                values
        );
    }

    private double variableElimination(
            List<FilteredTable.Factor> factors,
            int numVariables,
            int[] globalWidths) {

        List<FilteredTable.Factor> current =
                new ArrayList<>(factors);

        /*
         * Choose an elimination order.
         *
         * For now use a greedy min-fill-ish ordering based on
         * the number of variables introduced by eliminating a variable.
         */
        int[] order =
                chooseEliminationOrder(
                        current,
                        numVariables, globalWidths);

        for (int variable : order) {

            List<FilteredTable.Factor> containing = new ArrayList<>();
            List<FilteredTable.Factor> remaining = new ArrayList<>();

            for (FilteredTable.Factor f : current) {
                if (contains(f.vars, variable)) {
                    containing.add(f);
                } else {
                    remaining.add(f);
                }
            }

            if (containing.isEmpty()) {
                continue;
            }

            FilteredTable.Factor product =
                    multiplyFactors(
                            containing,
                            globalWidths);

            FilteredTable.Factor eliminated =
                    sumOut(
                            product,
                            variable);

            remaining.add(eliminated);
            current = remaining;
        }

        /*
         * Everything should now be scalar.
         */
        double result = 1.0;

        for (FilteredTable.Factor f : current) {
            if (f.vars.length != 0) {
                throw new IllegalStateException(
                        "Variable elimination left non-scalar factor"
                );
            }

            result *= f.values[0];
        }

        return result;
    }

    private FilteredTable.Factor multiplyFactors(
            List<FilteredTable.Factor> factors,
            int[] globalWidths) {

        if (factors.isEmpty()) {
            return new FilteredTable.Factor(
                    new int[0],
                    new int[0],
                    new double[]{1.0}
            );
        }

        if (factors.size() == 1) {
            return factors.get(0);
        }

        /*
         * Union of variables.
         */
        TreeSet<Integer> variableSet =
                new TreeSet<>();

        for (FilteredTable.Factor f : factors) {
            for (int v : f.vars) {
                variableSet.add(v);
            }
        }

        int[] vars =
                variableSet.stream()
                        .mapToInt(Integer::intValue)
                        .toArray();

        int[] widths = new int[vars.length];

        for (int i = 0; i < vars.length; i++) {
            widths[i] = globalWidths[vars[i]];
        }

        int total = 1;

        for (int width : widths) {
            total *= width;
        }

        double[] result =
                new double[total];

        int[] assignment =
                new int[globalWidths.length];

        for (int idx = 0; idx < total; idx++) {

            decodeLocalIndex(
                    idx,
                    vars,
                    widths,
                    assignment);

            double value = 1.0;

            for (FilteredTable.Factor f : factors) {
                value *= f.values[f.index(assignment)];

                if (value == 0.0) {
                    break;
                }
            }

            result[idx] = value;
        }

        return new FilteredTable.Factor(
                vars,
                widths,
                result);
    }

    private void decodeLocalIndex(
            int index,
            int[] vars,
            int[] widths,
            int[] assignment) {

        for (int i = vars.length - 1; i >= 0; i--) {
            assignment[vars[i]] =
                    index % widths[i];

            index /= widths[i];
        }
    }

    private FilteredTable.Factor sumOut(
            FilteredTable.Factor factor,
            int variable) {

        int pos = -1;

        for (int i = 0; i < factor.vars.length; i++) {
            if (factor.vars[i] == variable) {
                pos = i;
                break;
            }
        }

        if (pos < 0) {
            return factor;
        }

        /*
         * Variables remaining after elimination.
         */
        int[] newVars =
                new int[factor.vars.length - 1];

        int[] newWidths =
                new int[factor.widths.length - 1];

        int p = 0;

        for (int i = 0; i < factor.vars.length; i++) {
            if (i == pos) {
                continue;
            }

            newVars[p] = factor.vars[i];
            newWidths[p] = factor.widths[i];
            p++;
        }

        int newSize = 1;

        for (int width : newWidths) {
            newSize *= width;
        }

        double[] result =
                new double[newSize];

        /*
         * Scalar result.
         */
        if (newVars.length == 0) {

            double sum = 0.0;

            for (double value : factor.values) {
                sum += value;
            }

            result[0] = sum;

            return new FilteredTable.Factor(
                    newVars,
                    newWidths,
                    result);
        }

        /*
         * General case.
         */
        int[] assignment =
                new int[findMaxVariable(factor) + 1];

        int totalNew = result.length;

        for (int idx = 0; idx < totalNew; idx++) {

            decodeLocalIndex(
                    idx,
                    newVars,
                    newWidths,
                    assignment);

            double sum = 0.0;

            for (int b = 0;
                 b < factor.widths[pos];
                 b++) {

                assignment[variable] = b;

                sum += factor.values[
                        factor.index(assignment)
                        ];
            }

            result[idx] = sum;
        }

        return new FilteredTable.Factor(
                newVars,
                newWidths,
                result);
    }

    private int findMaxVariable(FilteredTable.Factor f) {
        int max = 0;

        for (int v : f.vars) {
            max = Math.max(max, v);
        }

        return max;
    }

    private boolean contains(
            int[] values,
            int value) {

        for (int x : values) {
            if (x == value) {
                return true;
            }
        }

        return false;
    }

    private int[] chooseEliminationOrder(
            List<FilteredTable.Factor> factors,
            int numVariables,
            int[] globalWidths) {

        List<Integer> remaining =
                new ArrayList<>();

        for (int v = 0; v < numVariables; v++) {
            remaining.add(v);
        }

        List<Integer> order =
                new ArrayList<>();

        while (!remaining.isEmpty()) {

            int best = -1;
            long bestCost = Long.MAX_VALUE;

            for (int candidate : remaining) {

                TreeSet<Integer> neighbors =
                        new TreeSet<>();

                for (FilteredTable.Factor f : factors) {

                    if (!contains(f.vars, candidate)) {
                        continue;
                    }

                    for (int v : f.vars) {
                        if (v != candidate) {
                            neighbors.add(v);
                        }
                    }
                }

                long cost = 1;

                for (int v : neighbors) {
                    cost *= globalWidths[v];
                }

                if (cost < bestCost) {
                    bestCost = cost;
                    best = candidate;
                }
            }

            order.add(best);
            remaining.remove(Integer.valueOf(best));
        }

        return order.stream()
                .mapToInt(Integer::intValue)
                .toArray();
    }

//    private boolean isPkForJoinKey(FilteredTable ft, String globalJK) {
//        SynopsisQuery.ColumnKey ck = getPK(ft.t, ft.unqColumns);
//        if (ck.column().isEmpty()) {
//            return false;
//        }
//        String column = jd.globalJoinKeyToTableColumn.get(globalJK).get(ft.t.getName());
//        return Objects.equals(ck.column(), column);
//    }


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

    public long getDatasetMemory() {
        long ram =0;
        for (ChowMinAllPairs sketch : cmj.values()) {
            ram += sketch.getN() * sketch.getNumColumns()*32L;
        }
        return ram;

    }

}
