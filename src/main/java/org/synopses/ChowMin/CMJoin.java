package org.synopses.ChowMin;

import org.dataset.JoinDataset;
import org.dataset.SynopsisQuery;
import org.duckdb.DuckDBConnection;
import org.utils.Config;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;

public class CMJoin {
    HashMap<String, ChowMinAllPairs> cmj;
    HashMap<String, String[]> attrMap;

    public CMJoin() {
        cmj = new HashMap<>();
        attrMap = new HashMap<>();
    }

    public void initialize(JoinDataset d, int ram, int depth,DuckDBConnection con) {
        HashMap<String, Integer> numColumnsPerTable = d.numColumnsPerTable;

        for (String t : d.tableNames) {
            ChowMinAllPairs syn = new ChowMinAllPairs(t, numColumnsPerTable.get(t), depth, ram);

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

    public int query(SynopsisQuery synQuery) {
        // #TODO: query CMJoin
        // 0. for each repetition in [d]
        // 1. select tables/chowmins for query
        // 2. apply predicates selecting for the eq join key something that it has to multiply with
        // 3. join size is estimated by inference procedure over the chowmin hash codomains per eq join key and multiplying with the local predicate tensors.
        return 0;
    }
}
