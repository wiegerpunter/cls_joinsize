package org.experiments;

import com.opencsv.CSVWriter;
import org.experiments.parameterSetting.RamToPar;
import org.synopses.*;
import org.synopses.ChowMin.ChowMinAllPairs;
import org.synopses.ChowMin.ChowMinCountSketchAllPairs;
import org.utils.Config;
import org.utils.Edge;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

public class RunSingleTableExperiment {
    private final String folder = "/home/wieger/sandboxChowMin/results/";
    private final String CSV_FILE_NAME = folder + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + ".csv";


    public int[][] data;
    private int[][] queries;
    private int[] exactAnswer;

    Config config;
    public RunSingleTableExperiment(Config config, int[][] data, int[][] queries, int[] exactAnswer) {

        this.config = config;
        this.data = data;
        this.queries = queries;
        this.exactAnswer = exactAnswer;

    }


    public void runAllExperiments(int repetition,  double[][] MIs, List<Edge> chowLiuTree, int maxNumPredicates) throws Exception {
        RamToPar rtp = new RamToPar(config.numStoredAttributes, config.ramVals);
        for (int ram : config.ramVals) {
            for (String experimentName : getEnabledExperiments()) {
                Experiment experiment = ExperimentFactory.getExperiment(experimentName);
                    if (experiment != null) {
                        experiment.setRunExperiments(this);
                        if (experimentName.contains("chowMin")) {
                            for (int j = 0; j < config.num_rows.length; j++) {
                                rtp.runChowMin(config.num_rows[j]);
                                experiment.run(ram, rtp, repetition, config);
                                config.depth = config.num_rows[j];

                            }
                        } else if (experimentName.contains("aSH")) {
                            for (double bufferASH : config.ingestBuffers) {
                                config.bufferASH = bufferASH;
                                experiment.run(ram, rtp, repetition, config);
                            }
                        } else {
                            experiment.run(ram, rtp, repetition, config);
                        }
                }
            }

//            for (int j = 0; j < config.num_rows.length; j++) {
//                ChowMinCountSketchAllPairs ChowLiuCountAllPairs = new ChowMinCountSketchAllPairs(data[0].length, config.num_rows[j], ram, MIs);
//                runSynopsis(ChowLiuCountAllPairs, data, queries, exactAnswer);
//            }

//
//            for (int j = 0; j < config.ingest_buffers.length; j++) {
//                double ingestBuffer = config.ingest_buffers[j];
//                aSH ash = new aSH(ram, data[0].length, useBufferInQuery, ingestBuffer, config.seed + 3);
//                runSynopsis(ash, data, queries, exactAnswer);
//            }
//            ReservoirSample rs = new ReservoirSample(ram, data[0].length, config.seed+5);
//            runSynopsis(rs,  data, queries, exactAnswer);
        }
//
//        PlotRunner.main(new String[]{CSV_FILE_NAME});
    }

    private String[] getColumnNames(int numAttributes) {
        String[] columns = new String[numAttributes];
        for (int c = 0; c < columns.length; c++) {
            columns[c] = String.valueOf(c);
        }
        return columns;
    }

    private static HashMap<String, Integer> readWidthMap(int ram, int d, int numAttributes) {
        HashMap<String, Integer> map = new HashMap<>();
        int numCMs = numAttributes * numAttributes/2 - numAttributes/2;
        int cells = ram / 4;
        int perCM = cells / (d * numCMs);
        int width = (int) Math.sqrt(perCM);
        for (int i = 0; i < numAttributes; i++) {
            map.put(String.valueOf(i), width);
        }
        return map;
    }

    private void differenceMIs(double[][] mIs, double[][] estimatedMIs) {
        // Difference in MIs itself:
            // 1. get total MIs
            // 2. per pair, normalize by total.
            // 3. MI - estMI
            // 4. print
        double totalExactMI = totalMI(mIs);
        double totalEstMI = totalMI(estimatedMIs);
        double[] differences = new double[mIs.length];
        double sumDifference = 0;
        for (int i = 0; i < mIs.length; i++) {
            differences[i] = mIs[i][2]/totalExactMI - estimatedMIs[i][2]/totalEstMI;
            sumDifference += Math.abs(differences[i]);
            System.out.println("MI: " + mIs[i][0] + ", " + mIs[i][1] + "=  " + mIs[i][2] +" , estimate: " + estimatedMIs[i][2] + " Difference " +  + differences[i]);
        }

        System.out.println("Total absolute difference: " + sumDifference);
    }

    double totalMI(double[][] mIs) {
        double totalMI = 0;
        for (double[] d: mIs) {
            totalMI += d[2];
        }
        return totalMI;
    }

    public static double[][] estimateMIs(int[][] data, int ram, int[] params) {

        ChowMinAllPairs ChowLiuMinAllPairs = new ChowMinAllPairs(ram, data[0].length, params);

        for (int[] datum : data) {
            ChowLiuMinAllPairs.addIntArr(datum);
        }
        return ChowLiuMinAllPairs.estimateMIs();
    }

    public void runSynopsis(AbstractSynopsis nm, int repetition) throws IOException {

        System.out.println(" -------------------------------------");
        System.out.println("Synopsis: "+ nm.setting + ", RAM: " + nm.ram);
        long start = System.currentTimeMillis();
        for (int[] datum : data) {
            nm.addIntArr(datum);
        }
        long end = System.currentTimeMillis();
        int ingestTime = (int) (end - start);
        System.out.println(nm.setting + " Ingestion time: " + ingestTime + " ms");

        int[] estimateNaiveMin = new int[queries.length];
        int[] errorNaiveMin = new int[queries.length];
        int[] numLatentAttrs = new int[queries.length];

        int[] queryTime = new int[queries.length];
        for (int i = 0; i < queries.length; i++) {
            start = System.currentTimeMillis();
            estimateNaiveMin[i]= nm.query(queries[i]);
            end = System.currentTimeMillis();
            queryTime[i] = (int) (end - start);
            numLatentAttrs[i] = nm.numLatent(queries[i]);
            errorNaiveMin[i] = estimateNaiveMin[i] - exactAnswer[i];
        }
        stats(exactAnswer, estimateNaiveMin, errorNaiveMin, numLatentAttrs, queryTime);
        int memUsage = nm.memoryUsage();
        writeResults(nm.setting, queries, estimateNaiveMin, exactAnswer, errorNaiveMin,
                memUsage,numLatentAttrs,ingestTime, queryTime, nm.getd(), nm.getw(), nm.getNumSamples(), nm.ram, data[0].length, data.length, repetition);
    }

    private List<String> getEnabledExperiments() {
        List<String> enabledExperiments = new ArrayList<>();
        if (config.expReadFile) enabledExperiments.add("readFile"); //always measure readFile time.
        if (config.expResSample) enabledExperiments.add("ReservoirSampling");
        if (config.expASH) enabledExperiments.add("aSH");
        if (config.expChowMin) enabledExperiments.add("chowMin");

        return enabledExperiments;
    }

    private void writeResults(String setting, int[][] queries, int[] estimates, int[] exactAnswers,
                              int[] errors, int memUsage,int[] numLatentAttrs, int ingestTime, int[] queryTime,
                              int d, int w, int numSamples, int ram, int numAttributes, int N, int repetition) throws IOException {


        boolean init = !new File(CSV_FILE_NAME).exists();
        //String currentDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());

        CSVWriter writer = new CSVWriter(new FileWriter(CSV_FILE_NAME, true));
        if (init) {
            String[] header = new String[]{"queryID","setting","memUsage","numLatentAttrs", "ingestTime","queryTime","estimate","exact","error","absError","relError","d","w","numSamples","ram","query","numPredicates","numAttributes","N","repetition"};
            writer.writeNext(header);
        }
        for  (int i = 0; i < estimates.length; i++) {
            String[] result = new String[20];
            result[0] = String.valueOf(i);
            result[1] = setting;
            result[2] = String.valueOf(memUsage);
            result[3] = String.valueOf(numLatentAttrs[i]);
            result[4] = String.valueOf(ingestTime);
            result[5] = String.valueOf(queryTime[i]);
            result[6] = String.valueOf(estimates[i]);
            result[7] = String.valueOf(exactAnswers[i]);
            result[8] = String.valueOf(errors[i]);
            result[9] = String.valueOf(Math.abs(estimates[i] - exactAnswers[i]));
            result[10] = String.valueOf(Math.abs(estimates[i] - exactAnswers[i])/exactAnswers[i]);
            result[11] = String.valueOf(d);
            result[12] = String.valueOf(w);
            result[13] = String.valueOf(numSamples);
            result[14] = String.valueOf(ram);
            result[15] = Arrays.toString(queries[i]);
            result[16] = numPredicates(queries[i]);
            result[17] = String.valueOf(numAttributes);
            result[18] = String.valueOf(N);
            result[19] = String.valueOf(repetition);

            writer.writeNext(result);
        }
        writer.close();

    }

    private String numPredicates(int[] query) {
        int numPredicates = 0;
        for (int q: query) {
            if (q != -1) {
                numPredicates++;
            }
        }
        return Integer.toString(numPredicates);
    }

    private void stats(
            int[] exactAnswer,
            int[] estimateRS,
            int[] errorRS,
            int[] latentAttrs,
            int[] queryTime) {

        Map<Integer, List<Integer>> absErrorByLatent = new HashMap<>();
        Map<Integer, List<Double>> relErrorByLatent = new HashMap<>();
        Map<Integer, List<Integer>> errorRSByLatent = new HashMap<>();
        Map<Integer, List<Integer>> qtimeByLatent = new HashMap<>();

        for (int i = 0; i < exactAnswer.length; i++) {
            int latent = latentAttrs[i];

            int absErr = Math.abs(exactAnswer[i] - estimateRS[i]);
            double relErr = (double) absErr / exactAnswer[i];

            absErrorByLatent
                    .computeIfAbsent(latent, k -> new ArrayList<>())
                    .add(absErr);

            relErrorByLatent
                    .computeIfAbsent(latent, k -> new ArrayList<>())
                    .add(relErr);

            errorRSByLatent
                    .computeIfAbsent(latent, k -> new ArrayList<>())
                    .add(errorRS[i]);

            qtimeByLatent
                    .computeIfAbsent(latent, k -> new ArrayList<>())
                    .add(queryTime[i]);
        }

        // Print stats per latent attribute
        for (int latent : absErrorByLatent.keySet()) {
            System.out.println("LatentAttr = " + latent);

            double[] errStats =
                    statsError((ArrayList<Integer>) errorRSByLatent.get(latent));
            System.out.printf("  Mean err: %f\n", errStats[0]);
            System.out.printf("  Variance err: %f\n", errStats[2]);

            double[] absStats =
                    statsError((ArrayList<Integer>) absErrorByLatent.get(latent));
            System.out.printf("  Mean absError: %f\n", absStats[0]);
            System.out.printf("  Variance absError: %f\n", absStats[2]);

            double[] relStats =
                    statsError(relErrorByLatent.get(latent));
            System.out.printf("  Mean relError: %f\n", relStats[0]);
            System.out.printf("  Variance relError: %f\n", relStats[2]);

            double[] qtimeStats =
                    statsError((ArrayList<Integer>) qtimeByLatent.get(latent));
            System.out.printf(" Mean query time: %f\n", qtimeStats[0]);
            System.out.printf(" Variance Query time: %f\n", qtimeStats[2]);
            System.out.println();
        }
    }

//    private void stats(int[] exactAnswer, int[] estimateRS, int[] errorRS, int[] latentAttrs) {
//        int[] absError = new int[errorRS.length];
//        double[] relError = new double[errorRS.length];
//        for (int i = 0; i < exactAnswer.length; i++) {
//            absError[i] += Math.abs(exactAnswer[i] - estimateRS[i]);
//            relError[i] += (double) Math.abs(exactAnswer[i] - estimateRS[i]) / exactAnswer[i];
//        }
//
//        double[] error = statsError(errorRS);
//
//        System.out.printf("Mean err: %f \n",error[0]);
//        System.out.printf("Variance: %f \n", error[2]);
//
//        double[] absErrors = statsError(absError);
//        System.out.printf("Mean absError: %f \n", absErrors[0]);
//        System.out.printf("Variance absError: %f \n", absErrors[2]);
//
//        double[] relErrors = statsError(relError);
//        System.out.printf("Mean relError: %f \n", relErrors[0]);
//        System.out.printf("Variance relError: %f \n", relErrors[2]);
//
//    }



    private double[] statsError(List<Double> error) {
        double sum=0;
        double variance=0;
        for (double err : error) {
            sum+=err;
            variance+=err*err;
        }

        return new double[]{ sum / error.size(), sum, variance/error.size()};
    }
    private static double[] statsError(ArrayList<Integer> error) {
        int sum=0;
        double variance=0;
        for (int err : error) {
            sum+=err;
            variance+=err*err;
        }

        return new double[]{(double) sum / error.size(), sum, variance/error.size()};

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
