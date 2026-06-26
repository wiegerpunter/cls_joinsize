package org.experiments;

import org.dataset.JoinDataset;
import org.duckdb.DuckDBConnection;
import org.synopses.ReservoirSampleString;
import org.utils.Config;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;

public class RunJoinExperiment {
    Config config;
    DuckDBConnection con;
    JoinDataset d;

    public RunJoinExperiment(Config config, JoinDataset d, DuckDBConnection con) throws SQLException {
        this.config = config;
        this.d = d;
        this.con = con;

        for (int ram : config.ramVals) {
            System.out.println("Starting iteration over " + ram / 1000000 + " MB");
            HashMap<String, ReservoirSampleString> rsj = new HashMap<>();
            for (String t : d.tableNames) {
                System.out.println("Initializing synopsis for table " + t);
                rsj.put(t, new ReservoirSampleString(ram, d.numColumnsPerTable.get(t), config.seed + 5));
            }
            ingestData(rsj);

        }
    }

    private void ingestData(HashMap<String, ReservoirSampleString> rsj) throws SQLException {
        // per table, insert all data into right synopsis
        for (String t : d.tableNames) {
            String dataQuery = "SELECT * FROM " + t;
            ReservoirSampleString syn = rsj.get(t);
            int numColumns = d.numColumnsPerTable.get(t);
            try (Statement stmt = con.createStatement();
                ResultSet rs = stmt.executeQuery(dataQuery);
            ) {
                while (rs.next()) {
                    String[] rec = new String[numColumns];
                    for (int col = 1; col <= numColumns; col++) {
                        rec[col - 1] = rs.getString(col);
                    }
                    syn.add(rec);
                }
            }
        }
    }


}