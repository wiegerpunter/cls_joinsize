package org.synopses.factorJoin;

import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import org.dataset.SynopsisQuery;

import java.util.*;

public class FilteredTableASH {
    public final List<SynopsisQuery.ColumnKey> unqColumns;
    public Table t;
    SynopsisQuery q;
    aSH s;
    public double sumView =0;
    double[][] view;
    public boolean isPrimaryKeyTable = false;
    public boolean pkHasPred = false;
    double[] pkView;
    boolean[] pkJoinKey;
    int[] globalJoinKeyIdx;
    final int[] joinKeyWidths;

    public FilteredTableASH(Table t, SynopsisQuery query, aSH s, Map<SynopsisQuery.ColumnKey, String> globalColToJoinKey) {
        this.t = t;
        this.q = query;
        this.s = s;
        this.unqColumns = unqJoinKeysInTable();
        // this view must represent the filtered table after applying the predicates.
//        int k = unqColumns.size();
        int total = s.getTotalJoinAssignments(unqColumns);
        isPrimaryKeyTable = isPrimaryKeyJoinKey();
        globalJoinKeyIdx = new int[unqColumns.size()];
        pkJoinKey = new boolean[unqColumns.size()];

//        SynopsisQuery.ColumnKey pk = getPK(t, unqColumns);
        for (int i = 0; i < unqColumns.size(); i++) {
            SynopsisQuery.ColumnKey pk = getPK(t, unqColumns.get(i));
            SynopsisQuery.ColumnKey col = unqColumns.get(i);
            String globalJK = globalColToJoinKey.get(col);
            globalJoinKeyIdx[i] =
                    query.jkToBucket.get(globalJK);

            pkJoinKey[i] =
                    !pk.column().isEmpty()
                            && col.column().equals(pk.column());
        }

//        int[] queryJoinKeyWs = new int[k];
//        for (int i = 0; i < k; i++) {
//            int attr = s.getIdCol(unqColumns.get(i).column());
//            queryJoinKeyWs[i] = s.widthMap.get(attr);
//        }
        this.joinKeyWidths = new int[unqColumns.size()];

        for (int i = 0; i < unqColumns.size(); i++) {
            this.joinKeyWidths[i] = s.joinKeyBinMap.get(s.getIdCol(unqColumns.get(i).column())).nbins;
        }

        pkHasPred = pkHasPred();

        view = new double[total][];
        if (isPrimaryKeyTable) {
            pkView = new double[s.getPKw()];
        }

        aSH.queryInfo f = s.queryInfo(query.getSelectionPredicatesPerTable(t), this.unqColumns);
        if (s.count< s.maxSize) {
            System.out.println("Table " + t.getAlias().getName() + " has N=" + s.count  + ", but uses " + s.maxSize + " samples with size: " +s.size);
        } else {
            System.out.println("Table " + t.getAlias().getName() + " has sampling rate " + (double) s.maxSize / s.count  + " with N= " + s.count + " samples with size: " +s.size);
        }
        for (int idx = 0; idx < total; idx++) {
            int[] buckets = decodeIdx(idx, joinKeyWidths);
            HashMap<Integer, Integer> bucketPerAttribute = new HashMap<>(unqColumns.size());
            for (int i = 0; i < unqColumns.size();i++) {
                bucketPerAttribute.put(s.getIdCol(unqColumns.get(i).column()), buckets[i]);
            }
            double[] raw =  s.queryBucketAssignment(f.record(), bucketPerAttribute, f.active(), pkHasPred);
            view[idx] = raw;
            sumView +=raw[0];
        }

        if (isPrimaryKeyTable) {
            for (int idx = 0; idx < s.getPKw(); idx++) {
                int[] buckets = decodeIdx(idx, new int[]{s.getPKw()});
//                if (!isPK(s.columns[0])) {
//                    throw new RuntimeException("Columns[0] is " + s.columns[0]);
//                }
                double raw =  s.queryBucketMarginal(0, buckets[0]);//sketches[s.sketch_idx_first(0)].marginalOneRep(j, 0, buckets[0]);
                pkView[idx] = raw;
            }
        }
    }

    private SynopsisQuery.ColumnKey getPK(Table t, SynopsisQuery.ColumnKey c) {

        if (c.column().equals("Id") || c.column().equals("id")) {
            return c;
        }
        SynopsisQuery.ColumnKey k = isPKTPCH(t, c);
        if (!k.column().isEmpty()) {
            return k;
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

    public static int getIdx(int[] buckets, int[] w) {
        int idx = 0;
        int mult = 1;

        for (int i = 0; i < buckets.length; i++) {
            idx += buckets[i] * mult;
            mult *= w[i];
        }

        return idx;
    }
    public static int[] decodeIdx(int idx, int[] w) {
        int k = w.length;
        int[] buckets = new int[k];

        for (int i = 0; i < k; i++) {
            buckets[i] = idx % w[i];
            idx /= w[i];
        }

        return buckets;
    }

    private List<SynopsisQuery.ColumnKey> unqJoinKeysInTable() {
        Set<SynopsisQuery.ColumnKey> unqColumns = new HashSet<>();
        for (SynopsisQuery.JoinPredicate j : q.joins) {
            if (j.left().getTableName().equals(t.getAlias().getName())) {
                unqColumns.add(SynopsisQuery.ColumnKey.of(j.left(), q.aliasMap));
            }
            if (j.right().getTableName().equals(t.getAlias().getName())) {
                unqColumns.add(SynopsisQuery.ColumnKey.of(j.right(), q.aliasMap));
            }
        }
        List<SynopsisQuery.ColumnKey> unqColumnList = new ArrayList<>(unqColumns.size());
        unqColumnList.addAll(unqColumns);
        unqColumnList.sort(Comparator
                .comparing(SynopsisQuery.ColumnKey::table)
                .thenComparing(SynopsisQuery.ColumnKey::column));
        //TODO: verify with 0
        return unqColumnList;
    }

    public boolean isPK(SynopsisQuery.ColumnKey c) {
        if (c.column().equals("Id") || c.column().equals("id")) {
            // PK is join key in this query;
            return true;
        }
        return isPKTPCH(t, c.column());
    }

    public boolean isPrimaryKeyJoinKey() {
        for (SynopsisQuery.ColumnKey c : unqColumns) {
            if (isPK(c)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPKTPCH(Table t, String c) {
        String tname = t.getAlias().getName();
        if (tname.equals("c")) {
            if (c.equals("c_custkey")) {
                return true;
            }
        }
        if (tname.equals("o")) {
            if (c.equals("o_orderkey")) {
                return true;
            }
        }
        if (tname.equals("n")) {
            if (c.equals("n_nationkey")) {
                return true;
            }
        }
        if (tname.equals("r")) {
            if (c.equals("r_regionkey")) {
                return true;
            }
        }
        if (tname.equals("s")) {
            if (c.equals("s_suppkey")) {
                return true;
            }
        }
        if (tname.equals("p")) {
            return c.equals("p_partkey");
        }
        return false;
    }

    public boolean pkHasPred() {
        for (SynopsisQuery.ColumnKey c : unqColumns) {
            if (isPK(c)) {
                // PK is join key in this query;
                for (EqualsTo eq : q.selections) {
                    if (SynopsisQuery.ColumnKey.of((Column) eq.getLeftExpression(), q.aliasMap).table().equals(c.table())) {
                        return true;
                    }
                }
                return false;
            }
        }
        return false;
    }
}