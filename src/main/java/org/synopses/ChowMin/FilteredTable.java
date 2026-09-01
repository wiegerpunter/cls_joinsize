package org.synopses.ChowMin;

import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import org.dataset.SynopsisQuery;
import java.util.*;

public class FilteredTable {
    public final List<SynopsisQuery.ColumnKey> unqColumns;
    public Table t;
    SynopsisQuery q;
    ChowMinAllPairs s;
    public double sumView =0;
    double[][] view;
    public boolean isPrimaryKeyTable = false;
    public boolean pkHasPred = false;
    double[] pkView;

    public FilteredTable(int j, Table t, SynopsisQuery query, ChowMinAllPairs s) {
        this.t = t;
        this.q = query;
        this.s = s;
        this.unqColumns = unqJoinKeysInTable();
        // this view must represent the filtered table after applying the predicates.
        int k = unqColumns.size();
        int total = s.getTotalJoinAssignments();
        isPrimaryKeyTable = isPrimaryKeyJoinKey();
        pkHasPred = pkHasPred();

        view = new double[total][];
        if (isPrimaryKeyTable) {
            pkView = new double[s.getPKw()];
        }

        ChowMinAllPairs.factorization f = s.factorize(query.getSelectionPredicatesPerTable(t), this.unqColumns);

        for (int idx = 0; idx < total; idx++) {
            int[] buckets = decodeIdx(idx, s.joinKeyWs);
            HashMap<Integer, Integer> bucketPerAttribute = new HashMap<>(unqColumns.size());
            for (int i = 0; i < unqColumns.size();i++) {
                bucketPerAttribute.put(s.getIdCol(unqColumns.get(i).column()), buckets[i]);
            }
            double[] raw =  s.queryFactorsJoinBucket(j, f.factors(), f.record(), bucketPerAttribute, f.active(), pkHasPred);
            view[idx] = raw;
            sumView +=raw[0];
        }

        if (isPrimaryKeyTable) {
            for (int idx = 0; idx < s.getPKw(); idx++) {
                int[] buckets = decodeIdx(idx, new int[]{s.getPKw()});
                if (!s.columns[0].equals("Id") && !s.columns[0].equals("id")) {
                    throw new RuntimeException("Columns[0] is " + s.columns[0]);
                }
                int raw =  s.sketches[s.sketch_idx_first(0)].marginalOneRep(j, 0, buckets[0]);
                pkView[idx] = raw;
            }
        }
    }

    private String getPK(List<SynopsisQuery.ColumnKey> unqColumns) {
        for (SynopsisQuery.ColumnKey c : unqColumns) {
            if (c.column().equals("Id") || c.column().equals("id")) {
                return c.column();
            }
        }
        throw new RuntimeException("No PK column found in " + unqColumns.toString());
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

    public boolean isPrimaryKeyJoinKey() {
        for (SynopsisQuery.ColumnKey c : unqColumns) {
            if (c.column().equals("Id") || c.column().equals("id")) {
                // PK is join key in this query;
                return true;
            }
        }
        return false;
    }

    public boolean pkHasPred() {
        for (SynopsisQuery.ColumnKey c : unqColumns) {
            if (c.column().equals("Id") || c.column().equals("id")) {
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