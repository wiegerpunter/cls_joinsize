package org.synopses.ChowMin;

import net.sf.jsqlparser.schema.Table;
import org.dataset.SynopsisQuery;
import java.util.*;

public class FilteredTable {
    final List<SynopsisQuery.ColumnKey> unqColumns;
    Table t;
    SynopsisQuery q;
    ChowMinAllPairs s;
    double[] view;
    public FilteredTable(int j, Table t, SynopsisQuery query, ChowMinAllPairs s) {
        this.t = t;
        this.q = query;
        this.s = s;
        this.unqColumns = unqJoinKeysInTable();
        // this view must represent the filtered table after applying the predicates.
        int k = unqColumns.size();
        int total = (int) Math.pow(s.getw(), k);

        view = new double[total];

        for (int idx = 0; idx < total; idx++) {
            int[] buckets = decodeIdx(idx, k, s.getw());
            HashMap<Integer, Integer> bucketPerAttribute = new HashMap<>(unqColumns.size());
            for (int i = 0; i < unqColumns.size();i++) {
                bucketPerAttribute.put(s.getIdCol(unqColumns.get(i).column()), buckets[i]);
            }
            view[idx] = s.queryWithJoinB(
                    j,
                    query.getSelectionPredicatesPerTable(t),
                    this.unqColumns,
                    bucketPerAttribute);
        }
    }
    public static int getIdx(int[] buckets, int w) {
        int idx = 0;
        int mult = 1;

        for (int bucket : buckets) {
            idx += bucket * mult;
            mult *= w;
        }
        return idx;
    }
    public static int[] decodeIdx(int idx, int k, int w) {
        int[] buckets = new int[k];

        for (int i = 0; i < k; i++) {
            buckets[i] = idx % w;
            idx /= w;
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
        return unqColumnList;
    }

    public boolean isPrimaryKeyJoinKey() {
        for (SynopsisQuery.ColumnKey c : unqColumns) {
            if (c.column().equals("Id")) {
                // PK is join key in this query;
                return true;
            }
        }
        return false;
    }
}