package org.synopses.factorJoin;

import net.sf.jsqlparser.schema.Table;
import org.dataset.JoinDataset;
import org.dataset.SynopsisQuery;
import org.duckdb.DuckDBConnection;
import org.experiments.RunJoinExperiment;
import org.synopses.ChowMin.FilteredTable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import static org.synopses.ChowMin.FilteredTable.decodeIdx;
import static org.synopses.ChowMin.FilteredTable.getIdx;

public class FactorJoinASH {
    HashMap<String, aSH> sketch;
    HashMap<String, String[]> attrMap;
    public HashMap<String, int[]> parMap;
    HashMap<String, Integer> joinKeyWidthMap;
    public HashMap<String, HashMap<String, Integer>> widthMap;
    public String setting = "FactorJoin_aSH";
    HashMap<String, Integer> numColumnsPerTable;
    JoinDataset jd;
    int[] globalWidths;

    public FactorJoinASH(JoinDataset jd, HashMap<String, Integer> joinKeyWidthMap,
                         HashMap<String, int[]> parMap, HashMap<String, String[]> attrMap,
                         int repetition,
                         HashMap<String, RunJoinExperiment.Buckets> globalJoinKeyBins
                      ) {
        this.parMap = parMap;
        this.attrMap = attrMap;
        this.joinKeyWidthMap = joinKeyWidthMap;
        this.globalWidths = new int[joinKeyWidthMap.size()];
        for (String jk : joinKeyWidthMap.keySet()) {
            int j = Integer.parseInt(jk.substring(1));
            globalWidths[j] = joinKeyWidthMap.get(jk);
        }
        this.jd = jd;
        numColumnsPerTable = jd.numColumnsPerTable;
        sketch = new HashMap<>();

        for (String t : jd.tableNames) {

            aSH syn = new aSH(t, attrMap.get(t), jd.globalColToJoinKey, globalJoinKeyBins, true,
                    numColumnsPerTable.get(t), parMap.get(t), repetition, true);
            sketch.put(t, syn);
        }
    }

    public long ingest(DuckDBConnection con) {

        long start = System.currentTimeMillis();
        for (String t : jd.tableNames) {
            System.out.println("Ingesting table " + t);
            aSH syn = sketch.get(t);
            int numColumns = jd.numColumnsPerTable.get(t);
            String dataQuery = "SELECT * FROM " + t;
            try (Statement stmt = con.createStatement();
                 ResultSet rs = stmt.executeQuery(dataQuery)
            ) {
                while (rs.next()) {
                    int[] rec = new int[numColumns];
                    for (int col = 1; col <= numColumns; col++) {
                        if (syn.isJoinKey[col-1]){
                            if (rs.getString(col) != null) {
                                rec[col - 1] = Integer.parseInt(rs.getString(col));
                            } else {
                                rec[col - 1] = -999;
                            }
                        } else {
                            if (rs.getString(col) != null) {
                                rec[col - 1] = rs.getString(col).hashCode();
                            } else {
                                rec[col-1]=-999;
                            }
                        }
                    }
                    int sign = Integer.parseInt(rs.getString(numColumns+2));
                    syn.ingestArr(rec, sign);
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            sketch.put(t, syn);
        }
        long end = System.currentTimeMillis();
        return end-start;
    }

    public double query(SynopsisQuery synQuery) {
        // 1. select tables for query
        // 2. apply predicates selecting for the eq join key something that it has to multiply with
        // 3. join size is estimated by inference procedure over the RS equi-width domains per eq join key
        // and multiplying with the local predicate tensors.
        // All unique join keys participating in the query.
        List<String> joinKeys = new ArrayList<>(synQuery.globalJoinKeysInQuerySet);

        Map<Double, Map<String, FilteredTableASH>> estToFilteredMap = new HashMap<>();
        Map<String, FilteredTableASH> filtered = filterTables(synQuery); // applying local predicates.
        List<Factor> factors = buildFactors(filtered, globalWidths, synQuery.bucketToJK);
        double varEst = variableElimination(factors, globalWidths.length, globalWidths);
//        double oldEst = estimateJoinSize(synQuery, filtered, joinKeys);
//        if (Math.abs(varEst - oldEst) >=1000) {
//           System.out.println("Var EL not equal to old estimate");
//        }
        return varEst;
    }
    private Map<String, FilteredTableASH> filterTables(SynopsisQuery query) {
        HashMap<String, FilteredTableASH> map = new HashMap<>();
        for (Table t: query.tables) {
            map.put(t.getAlias().getName(), new FilteredTableASH(t, query, sketch.get(t.getName()), jd.globalColToJoinKey));
        }
        return map;
    }
//
//    private int[] getTableJoinKeyWidths(FilteredTable ft) {
//        int[] widths = new int[ft.unqColumns.size()];
//
//        for (int i = 0; i < ft.unqColumns.size(); i++) {
//            widths[i] = widthMap.get(ft.t.getName()).get(ft.unqColumns.get(i).column());
//        }
//
//        return widths;
//    }

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
            Map<String, FilteredTableASH> filtered, List<String> joinKeys) {

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
        for (FilteredTableASH ft : filtered.values()) {
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

            for (FilteredTableASH ft : filtered.values()) {

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

            for (FilteredTableASH ft : filtered.values()) {

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

            for (FilteredTableASH ft : filtered.values()) {

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


    static class Factor {
        // Global join-key IDs occurring in this factor.
        final int[] vars;

        // Number of buckets for each variable.
        final int[] widths;

        // Row-major / mixed-radix storage.
        final double[] values;

        Factor(int[] vars, int[] widths, double[] values) {
            this.vars = vars;
            this.widths = widths;
            this.values = values;
        }

        int size() {
            return values.length;
        }

        int index(int[] assignment) {
            int idx = 0;
            for (int i = 0; i < vars.length; i++) {
                idx = idx * widths[i] + assignment[vars[i]];
            }
            return idx;
        }
    }


    private List<Factor> buildFactors(
            Map<String, FilteredTableASH> filtered,
            int[] globalWidths,
            HashMap<Integer, Integer> bucketToJK) {

        List<Factor> factors =
                new ArrayList<>();

        /*
         * First add the table factors.
         */
        for (FilteredTableASH ft : filtered.values()) {

            factors.add(
                    makeTableFactor(
                            ft,
                            globalWidths,
                            bucketToJK));
        }

        for (FilteredTableASH ft : filtered.values()) {

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
                FilteredTableASH pkTable = null;

                for (FilteredTableASH candidate : filtered.values()) {

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

    private Factor makeTableFactor(
            FilteredTableASH ft,
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

        return new Factor(vars, widths, values);
    }

    private Factor makeCountFactor(
            FilteredTableASH ft,
            int[] globalWidths) {

        int k = ft.globalJoinKeyIdx.length;

        int[] vars = ft.globalJoinKeyIdx.clone();
        int[] widths = ft.joinKeyWidths.clone();

        double[] values = new double[ft.view.length];

        for (int i = 0; i < ft.view.length; i++) {
            values[i] = ft.view[i][0];
        }

        return new Factor(vars, widths, values);
    }

    private Factor makeSelectedFactor(
            FilteredTableASH ft) {

        int k = ft.globalJoinKeyIdx.length;

        int[] vars = ft.globalJoinKeyIdx.clone();
        int[] widths = ft.joinKeyWidths.clone();

        double[] values = new double[ft.view.length];

        for (int i = 0; i < ft.view.length; i++) {
            values[i] = ft.view[i][1];
        }

        return new Factor(vars, widths, values);
    }


    private Factor makePKFactor(
            FilteredTableASH ft,
            int globalJK,
            int width) {

        double[] values = new double[width];

        for (int b = 0; b < width; b++) {
            values[b] = getMarginal(ft, b);
        }

        return new Factor(
                new int[]{globalJK},
                new int[]{width},
                values
        );
    }

    private Factor makeInversePKFactor(
            FilteredTableASH ft,
            int globalJK,
            int width) {

        double[] values = new double[width];

        for (int b = 0; b < width; b++) {
            double x = getMarginal(ft, b);

            values[b] = x > 0.0 ? 1.0 / x : 0.0;
        }

        return new Factor(
                new int[]{globalJK},
                new int[]{width},
                values
        );
    }

    private double variableElimination(
            List<Factor> factors,
            int numVariables,
            int[] globalWidths) {

        List<Factor> current =
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

            List<Factor> containing = new ArrayList<>();
            List<Factor> remaining = new ArrayList<>();

            for (Factor f : current) {
                if (contains(f.vars, variable)) {
                    containing.add(f);
                } else {
                    remaining.add(f);
                }
            }

            if (containing.isEmpty()) {
                continue;
            }

            Factor product =
                    multiplyFactors(
                            containing,
                            globalWidths);

            Factor eliminated =
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

        for (Factor f : current) {
            if (f.vars.length != 0) {
                throw new IllegalStateException(
                        "Variable elimination left non-scalar factor"
                );
            }

            result *= f.values[0];
        }

        return result;
    }

    private Factor multiplyFactors(
            List<Factor> factors,
            int[] globalWidths) {

        if (factors.isEmpty()) {
            return new Factor(
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

        for (Factor f : factors) {
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

            for (Factor f : factors) {
                value *= f.values[f.index(assignment)];

                if (value == 0.0) {
                    break;
                }
            }

            result[idx] = value;
        }

        return new Factor(
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

    private Factor sumOut(
            Factor factor,
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

            return new Factor(
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

        return new Factor(
                newVars,
                newWidths,
                result);
    }

    private int findMaxVariable(Factor f) {
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
            List<Factor> factors,
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

                for (Factor f : factors) {

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
            FilteredTableASH ft,
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

    public int getMemory() {
        int ram = 0;
        for (aSH sketch : sketch.values()) {
            ram+= sketch.getMemoryUsage();
        }
        return ram;
    }

    public long getDatasetMemory() {
        long ram =0;
        for (aSH sketch : sketch.values()) {
            ram += sketch.getN() * sketch.getNumColumns()*32L;
        }
        return ram;

    }

}
