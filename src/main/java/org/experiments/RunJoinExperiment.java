package org.experiments;

import org.dataset.JoinDataset;
import org.dataset.Query;
import org.dataset.SynopsisQuery;
import org.duckdb.DuckDBConnection;
import org.synopses.ChowMin.CMJoin;
import org.synopses.RS.RSJoin;
import org.synopses.RS.ReservoirSampleString;
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

//        for (int ram : config.ramVals) {
//            System.out.println("Starting iteration over " + ram / 1000000 + " MB");
//            RSJoin rsj = new RSJoin();
//            rsj.initialize(d.tableNames, d.attrMap, ram, d.numColumnsPerTable, config);
//            rsj.ingestData(d, con);
//            queryRS(rsj);
//        }
        for (int ram : config.ramVals) {
            for (int j = 0; j < config.num_rows.length; j++) {
                System.out.println("Starting iteration over " + ram / 1000000 + " MB");
                CMJoin cmj = new CMJoin();
                cmj.initialize(d, d.attrMap, ram, config.num_rows[j], con);
                queryCM(cmj);
            }
        }
    }



    private void queryRS(RSJoin rsj) {
        // execute queries in d on reservoir samples


        for (Query q : d.getQueries()) {
            int estimate = rsj.query(q.syn_query);
            double error = estimate - q.getResult();
            System.out.println("Query " + q.getName() + " error: " + error + " exact: " + q.getResult() + " estimate: " +estimate);
        }

    }
    private void queryCM(CMJoin cmj) {
        // execute queries in d on reservoir samples


        for (Query q : d.getQueries()) {
            double estimate = cmj.query(q.syn_query);
            double error = estimate - q.getResult();
            System.out.println("Query " + q.getName() + " error: " + error + " exact: " + q.getResult() + " estimate: " +estimate);
        }

    }


}