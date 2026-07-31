package org.synopses.ChowMin;

import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import org.dataset.JoinDataset;
import org.dataset.SynopsisQuery;
import org.duckdb.DuckDBConnection;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.logging.Filter;

import static org.synopses.ChowMin.FilteredTable.decodeIdx;
import static org.synopses.ChowMin.FilteredTable.getIdx;

public class CMJoin {
    HashMap<String, ChowMinAllPairs> cmj;
    HashMap<String, String[]> attrMap;
    int depth;
    int width;

    public CMJoin() {
        cmj = new HashMap<>();
        attrMap = new HashMap<>();
    }

    public void initialize(JoinDataset d, HashMap<String, String[]> attrMap, int ram, int depth, int width, DuckDBConnection con) {
        this.depth = depth;
        this.width = width;
        HashMap<String, Integer> numColumnsPerTable = d.numColumnsPerTable;

        for (String t : d.tableNames) {
            ChowMinAllPairs syn = new ChowMinAllPairs(t, attrMap.get(t), numColumnsPerTable.get(t), depth, width, ram);

            int numColumns = d.numColumnsPerTable.get(t);
            String dataQuery = "SELECT * FROM " + t;
            try (Statement stmt = con.createStatement();
                 ResultSet rs = stmt.executeQuery(dataQuery);
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
            syn.setMIs(syn.estimateMIs(), true);

            cmj.put(t, syn);

    }
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
        return estimates[depth/2];
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
        // Enumerate all possible codomains of the equivalent join keys
        int numJoinKeys = query.joinKeyToTableColumn.size();
        int totalBuckets = (int) Math.pow(w, numJoinKeys);
        System.out.println("Join with "+ numJoinKeys + " has " + totalBuckets + " options");
        int[][] tableBuckets = new int[filtered.size()][];
        int t=0;
        for (FilteredTable ft:filtered.values()) {
            tableBuckets[t] = new int[ft.globalJoinDims.length];
            t++;
        }
        for (int idx = 0; idx < totalBuckets; idx++) {
            int[] bucketAssignment = decodeIdx(idx, numJoinKeys, w);
            double product = 1.0;
            int tloop=0;
            for (FilteredTable ft : filtered.values()) {
                for (int i = 0; i < ft.globalJoinDims.length; i++) {
                    tableBuckets[tloop][i] = bucketAssignment[ft.globalJoinDims[i]];
                }
                int viewIdx = getIdx(tableBuckets[tloop], w);
                product *= ft.view[viewIdx];
                tloop++;
            }
            estimate += product;
        }
        return estimate;
    }
}
