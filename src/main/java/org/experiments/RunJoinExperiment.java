package org.experiments;

import com.opencsv.CSVWriter;
import org.dataset.JoinDataset;
import org.dataset.Query;
import org.duckdb.DuckDBConnection;
import org.synopses.ChowMin.CMJoin;
import org.synopses.RS.RSJoin;
import org.utils.Config;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class RunJoinExperiment {
    Config config;
    DuckDBConnection con;
    JoinDataset d;
    private final String folder = "/home/wieger/sandboxChowMin/resultsJoinSize/";
    private final String CSV_FILE_NAME = folder + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + ".csv";

    public RunJoinExperiment(Config config, JoinDataset d, DuckDBConnection con) throws Exception {
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

        for (int j = 0; j < config.num_rows.length; j++) {
            CMJoin cmj = new CMJoin();
            long ingestTime = cmj.initialize(d, d.attrMap, 0, config.num_rows[j], config.width, con);
            queryCM(cmj, ingestTime);
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
    private void queryCM(CMJoin cmj, long ingestTime) throws Exception {
        // execute queries in d on reservoir samples
        int numQueries = d.getQueries().length;
        String[] queryName = new String[numQueries];
        double[] estimates= new double[numQueries];
        double[] errors = new double[numQueries];
        double[] exact = new double[numQueries];
        double[] queryTime = new double[numQueries];
        for (int i = 0; i < numQueries; i++) {
            Query q = d.getQueries()[i];
            double estimate = cmj.query(q.syn_query);
            double error = estimate - q.getResult();
            estimates[i] = estimate;
            errors[i] = error;
            exact[i] = q.getResult();
            queryName[i] = q.getName();
        }

        writeResults(cmj.setting, queryName, estimates, exact, errors, cmj.getDepth(), cmj.getWidth(), cmj.getMemory(),
                cmj.getDatasetMemory(), ingestTime);
    }

    private void writeResults(String setting,String[] queryName, double[] estimates, double[] exact,
                              double[] errors, int d, int w, int ram, int datasetMemory,
                              long ingestTime) throws Exception {


        boolean init = !new File(CSV_FILE_NAME).exists();

        CSVWriter writer = new CSVWriter(new FileWriter(CSV_FILE_NAME, true));
        if (init) {
            String[] header = new String[]{"queryID","setting","memUsage","estimate","exact","error","absError","relError",
                    "d","w","datasetMemory","ingestTime"};
            writer.writeNext(header);
        }
        for  (int i = 0; i < estimates.length; i++) {
            String[] result = new String[12];
            result[0] = queryName[i];
            result[1] = setting;
            result[2] = String.valueOf(ram);
            result[3] = String.valueOf(estimates[i]);
            result[4] = String.valueOf(exact[i]);
            result[5] = String.valueOf(errors[i]);
            result[6] = String.valueOf(Math.abs(estimates[i] - exact[i]));
            result[7] = String.valueOf(Math.abs(estimates[i] - exact[i])/exact[i]);
            result[8] = String.valueOf(d);
            result[9] = String.valueOf(w);
            result[10] = String.valueOf(datasetMemory);
            result[11] = String.valueOf(ingestTime);

            writer.writeNext(result);
        }
        writer.close();

    }


}