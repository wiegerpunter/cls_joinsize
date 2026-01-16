package org.experiments;

import com.opencsv.CSVWriter;
import org.synopses.*;
import org.synopses.ChowMin.ChowM2D;
import org.utils.Config;
import org.utils.Edge;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class RunExperiment {
    private final String folder = "/home/wieger/sandboxChowMin/results/";
    private final String CSV_FILE_NAME = folder + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + ".csv";

    Config config;
    public RunExperiment(Config config, int[][] data, int[][] queries, int[] exactAnswer, List<Edge> chowLiuTree) throws IOException {
        this.config = config;

        for (int ram : config.ramVals) {
            NaiveMin naiveMin = new NaiveMin(config.cardinalities.length,ram);
            runSynopsis(naiveMin, data, queries, exactAnswer);

            CM2D naiveMin2D = new CM2D(config.cardinalities.length, ram);
            runSynopsis(naiveMin2D, data, queries, exactAnswer);

            ChowM2D ChowLiuMin2D = new ChowM2D(config.cardinalities.length, ram, chowLiuTree);
            runSynopsis(ChowLiuMin2D, data, queries, exactAnswer);

            ReservoirSample rs = new ReservoirSample(ram, config.cardinalities.length);
            runSynopsis(rs,  data, queries, exactAnswer);
        }
    }


    private void runSynopsis(AbstractSynopsis nm, int[][] data, int[][] queries, int[] exactAnswer) throws IOException {

        long start = System.currentTimeMillis();
        for (int[] datum : data) {
            nm.add(datum);
        }
        long end = System.currentTimeMillis();
        int ingestTime = (int) (end - start);
        System.out.println("Naive min time: " + ingestTime + " ms");

        int[] estimateNaiveMin = new int[queries.length];
        int[] errorNaiveMin = new int[queries.length];

        int[] queryTime = new int[queries.length];
        for (int i = 0; i < queries.length; i++) {
            start = System.currentTimeMillis();
            estimateNaiveMin[i]= nm.query(queries[i]);
            end = System.currentTimeMillis();
            queryTime[i] = (int) (end - start);
            errorNaiveMin[i] = estimateNaiveMin[i] - exactAnswer[i];
        }
        stats(exactAnswer, estimateNaiveMin, errorNaiveMin);
        int memUsage = nm.memoryUsage();
        writeResults(nm.setting, estimateNaiveMin, exactAnswer, errorNaiveMin, memUsage,ingestTime, queryTime, nm.getd(), nm.getw(), nm.ram);
    }

    private void writeResults(String setting, int[] estimates, int[] exactAnswers, int[] errors, int memUsage, int ingestTime, int[] queryTime, int d, int w, int ram) throws IOException {


        boolean init = !new File(CSV_FILE_NAME).exists();
        //String currentDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

        CSVWriter writer = new CSVWriter(new FileWriter(CSV_FILE_NAME, true));
        if (init) {
            String[] header = new String[]{"queryID","setting","memUsage", "ingestTime","queryTime","estimate","exact","error","absError","relError","d","w","ram"};
            writer.writeNext(header);
        }
        for  (int i = 0; i < estimates.length; i++) {
            String[] result = new String[13];
            result[0] = String.valueOf(i);
            result[1] = setting;
            result[2] = String.valueOf(memUsage);
            result[3] = String.valueOf(ingestTime);
            result[4] = String.valueOf(queryTime[i]);
            result[5] = String.valueOf(estimates[i]);
            result[6] = String.valueOf(exactAnswers[i]);
            result[7] = String.valueOf(errors[i]);
            result[8] = String.valueOf(Math.abs(estimates[i] - exactAnswers[i]));
            result[9] = String.valueOf(Math.abs(estimates[i] - exactAnswers[i])/exactAnswers[i]);
            result[10] = String.valueOf(d);
            result[11] = String.valueOf(w);
            result[12] = String.valueOf(ram);

            writer.writeNext(result);
        }
        writer.close();

    }

    private void stats(int[] exactAnswer, int[] estimateRS, int[] errorRS) {
        int[] absError = new int[errorRS.length];
        double[] relError = new double[errorRS.length];
        for (int i = 0; i < exactAnswer.length; i++) {
            absError[i] += Math.abs(exactAnswer[i] - estimateRS[i]);
            relError[i] += (double) Math.abs(exactAnswer[i] - estimateRS[i]) / exactAnswer[i];
        }

        double[] error = statsError(errorRS);

        System.out.printf("Mean err: %f \n",error[0]);
        System.out.printf("Variance: %f \n", error[2]);

        double[] absErrors = statsError(absError);
        System.out.printf("Mean absError: %f \n", absErrors[0]);
        System.out.printf("Variance absError: %f \n", absErrors[2]);

        double[] relErrors = statsError(relError);
        System.out.printf("Mean relError: %f \n", relErrors[0]);
        System.out.printf("Variance relError: %f \n", relErrors[2]);

    }

    private static double[] statsError(int[] error) {
        int sum=0;
        double variance=0;
        for (int err : error) {
            sum+=err;
            variance+=err*err;
        }

        return new double[]{(double) sum / error.length, sum, variance/error.length};

    }
    private static double[] statsError(double[] error) {
        return getDoubles(error);

    }

    public static double[] getDoubles(double[] error) {
        double sum=0;
        double variance=0;
        for (double err : error) {
            sum+=err;
            variance+=err*err;
        }

        return new double[]{sum / error.length, sum, variance/error.length};
    }


}
