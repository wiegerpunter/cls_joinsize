package org;

import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;

import java.util.*;
import java.util.stream.*;

import static org.ChowLiu.chowLiu;


//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class TestCompQueries {

    static String folder = "/home/wieger/sandboxChowMin/results/";
    static String CSV_FILE_NAME = folder + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + ".csv";
    public static void main(String[] args) throws IOException {
//        int[] cardinalities = {100, 5, 5, 5};

        int[] cardinalities = {100, 100, 100, 100, 100, 100};
//        int[] cardinalities = {10,10,10,10};

//        double[][] corr = {
//                {1.0, 0.9, 0.4, 0.2},
//                {0.9, 1.0, 0.7, 0.1},
//                {0.4, 0.7, 1.0, 0.6},
//                {0.2, 0.1, 0.6, 1.0}
//        };
//        double[][] corr = {
//                {1.0, 1.0, 1.0, 1.0},
//                {1.0, 1.0, 1.0, 1.0},
//                {1.0,1.0, 1.0, 1.0},
//                {1.0, 1.0, 1.0, 1.0}
//        };

//
//        double[][] corr = {
//                {1.0, 0.0, 0.0, 0.0},
//                {0.0, 1.0, 0.0, 0.0},
//                {0.0, 0.0, 1.0, 0.0},
//                {0.0, 0.0, 0.0, 1.0}
//        };

//        double[][] corr = {
//                {1.0, 0.0},
//                {0.0, 1.0}
//        };
//        CorrelatedDataGenerator generator = new CorrelatedDataGenerator(10, cardinalities, corr);
        DataGenerator generator = new DataGenerator(1000000, cardinalities);
//        generator.generateDatasetUniform();
//        generator.generateDataPerfectFD();
        generator.generateDataCorr();
        generator.generatePairwiseQueries(500);
//        generator.generatePredQueries(500);

        int[][] data = generator.getDataset();
//        statsData(data);
        printSampleData(data);
        List<ChowLiu.Edge> chowLiu = computeExactMutualInformation(data);
        for (ChowLiu.Edge e : chowLiu) {
            System.out.printf("Edge (%d, %d)", e.u, e.v);
        }
        int[][] queries = generator.getQueries();
        int[] exactAnswer = generator.getExactAnswer();

        printSampleQueries(queries, exactAnswer);

        int[] ds = new int[]{3};
        int[] ws = new int[]{32};
        int[] rams = new int[]{10000,50000,100000,500000};
        for (int ram : rams) {
                NaiveMin naiveMin = new NaiveMin(cardinalities.length,ram);
                runNaiveMin(naiveMin, data, queries, exactAnswer);

                CM2D naiveMin2D = new CM2D(cardinalities.length, ram);
                runNaiveMin(naiveMin2D, data, queries, exactAnswer);

                ChowM2D ChowLiuMin2D = new ChowM2D(cardinalities.length, ram, chowLiu);
                runNaiveMin(ChowLiuMin2D, data, queries, exactAnswer);
        }

    }
    public static class Record {
        int[] rec;

        Record(int[] rec) {
            this.rec = rec;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true; // same object
            if (!(obj instanceof Record other)) return false;
            return Arrays.equals(this.rec, other.rec);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(rec);
        }
    }

    private static void statsData(int[][] data) {
        // cardinality per attribute
        // frequency for top-10 values

        HashMap<Record, Integer> uniqueRecords = new HashMap<>();
        HashMap<Integer, Integer>[] uniqueRecsPerAttr= new HashMap[data[0].length];
        for (int i = 0; i < data[0].length; i++) {
            uniqueRecsPerAttr[i] = new HashMap<>();
        }
        for (int[] datum : data) {
            Record r = new Record(datum);
            uniqueRecords.put(r, uniqueRecords.getOrDefault(r, 0) + 1);
            for (int attr = 0; attr < datum.length; attr++) {
                uniqueRecsPerAttr[attr].put(datum[attr], uniqueRecords.getOrDefault(r, 0) + 1);
            }
        }

        int K = 10;
        // freq topk vals
        List<Map.Entry<Record, Integer>> topKByValue = topKByValue(uniqueRecords, K);

        for (Map.Entry<Record, Integer> entry : topKByValue) {
            System.out.println(Arrays.toString(entry.getKey().rec) + " : " + entry.getValue());

        }

        for (int a = 0; a < data[0].length; a++) {
            List<Map.Entry<Integer, Integer>> topKByAttr = topKByValueInt(uniqueRecsPerAttr[a], K);
            System.out.println(" TOP " + K + " by attr " + a + " is: ");
            for (Map.Entry<Integer, Integer> entry : topKByAttr) {
                System.out.println(entry.getKey() + " : " + entry.getValue());
            }
        }

    }
    public static List<Map.Entry<Record, Integer>> topKByValue(Map<Record, Integer> map, int k) {
        return map.entrySet()
                .stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue())) // descending by value
                .limit(k)
                .collect(Collectors.toList());
    }
    public static List<Map.Entry<Integer, Integer>> topKByValueInt(Map<Integer, Integer> map, int k) {
        return map.entrySet()
                .stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue())) // descending by value
                .limit(k)
                .collect(Collectors.toList());
    }

    public static List<Map.Entry<Record, Integer>> topKByAttribute(Map<Record, Integer> map, int attrIndex, int k) {
        return map.entrySet()
                .stream()
                .sorted((a, b) -> Integer.compare(b.getKey().rec[attrIndex], a.getKey().rec[attrIndex])) // descending by rec[i]
                .limit(k)
                .collect(Collectors.toList());
    }

    private static List<ChowLiu.Edge> computeExactMutualInformation(int[][] data) {
        // goal: compute exact chow liu tree.

        double[][] MIs = new double[(data[0].length*data[0].length-data[0].length)/2][3];
        int count = 0;
        for (int i = 0; i < data[0].length; i++) {
            for (int j = i + 1; j < data[0].length; j++) {
                MIs[count] = new double[]{i,j,0};
                count++;
            }
        }

        // loop over MIs and compute mutual information for columns 0, 1 and update 2.

        for (int i = 0; i < MIs.length; i++) {
            MIs[i][2] = computeMI(data, (int) MIs[i][0], (int) MIs[i][1]);
            System.out.println("MI " + MIs[i][0] + " , " + MIs[i][1] +" = " + MIs[i][2]);
        }

        // get max span tree for ChowLiu:
        return chowLiu(MIs);

    }
    private static double computeMI(int[][] data, int v1, int v2) {
        int N = data.length;
        HashMap<Integer, Integer> uniqueV1 = new HashMap<>();
        HashMap<Integer, Integer> uniqueV2 = new HashMap<>();

        HashMap<Record, Integer> unique = new HashMap<>();

        for (int[] datum : data) {
            int v1Val = datum[v1];
            int v2Val = datum[v2];
            uniqueV1.put(v1Val, uniqueV1.getOrDefault(v1Val, 0) + 1);
            uniqueV2.put(v2Val, uniqueV2.getOrDefault(v2Val, 0) + 1);
            Record v1v2 = new Record(new int[]{v1Val, v2Val});
            unique.put(v1v2, unique.getOrDefault(v1v2, 0) + 1);
        }
        double mi= 0;
        for (Integer v1Val : uniqueV1.keySet()) {
            for  (Integer v2Val : uniqueV2.keySet()) {
                Record v1v2 = new Record(new int[]{v1Val, v2Val});
                double p_v1_v2 = (double) unique.getOrDefault(v1v2, 0) / N; // # co-occurences of v1, v2
                double p_v1 = (double) uniqueV1.get(v1Val) / N; // # occurences of v1
                double p_v2 = (double) uniqueV2.get(v2Val) / N; // # occurences of v2
                if (p_v1_v2 > 0)
                   mi += p_v1_v2 * Math.log(p_v1_v2 / (p_v1 * p_v2))/Math.log(2) ;
            }
        }
        return mi;
    }

//    public static double computeMI(int[][] data, int v1, int v2) {
//        int N = data.length;
//        if (N == 0) return 0.0;
//
//        // Frequency of v1 values
//        Map<Integer, Long> freqV1 = Arrays.stream(data)
//                .collect(Collectors.groupingBy(row -> row[v1], Collectors.counting()));
//
//        // Frequency of v2 values
//        Map<Integer, Long> freqV2 = Arrays.stream(data)
//                .collect(Collectors.groupingBy(row -> row[v2], Collectors.counting()));
//
//        // Joint frequency of (v1, v2)
//        Map<Record, Long> jointFreq = Arrays.stream(data)
//                .collect(Collectors.groupingBy(
//                        row -> new Record(new int[]{row[v1], row[v2]}),
//                        Collectors.counting()
//                ));
//
//        // Compute mutual information
//        return jointFreq.entrySet().stream()
//                .mapToDouble(e -> {
//                    double pXY = e.getValue() / (double) N;
//                    double pX = freqV1.get(e.getKey().rec[0]) / (double) N;
//                    double pY = freqV2.get(e.getKey().rec[1]) / (double) N;
//                    return (pXY == 0) ? 0 : pXY * Math.log(pXY / (pX * pY)) / Math.log(2);
//                })
//                .sum();
//    }

    private static void printSampleData(int[][] data) {


        System.out.println("Sample data:");
        for (int i = 0; i <Math.min(data.length, 10); i++) { //Math.min(data.length, 10)
            for (int j = 0; j < data[i].length; j++) {
                System.out.print(data[i][j] + " ");
            }
            System.out.println();
        }
    }

    private static void printSampleQueries(int[][] queries, int[] exactAnswer) {
        for (int i = 0; i < Math.min(queries.length, 10); i++) {
            for (int j = 0; j < queries[i].length; j++) {
                System.out.print(queries[i][j] + " ");
            }
            System.out.print(" exact: " + exactAnswer[i] + " ");
            System.out.println();
        }
    }

    private static void runNaiveMin(AbstractSynopsis nm, int[][] data, int[][] queries, int[] exactAnswer) throws IOException {

        long start = System.currentTimeMillis();
        for (int[] datum : data) {
            nm.add(datum);
        }
        long end = System.currentTimeMillis();
        int ingestTime = (int) (end - start);
        System.out.println("Naive min time: " + ingestTime + " ms");

        int[] estimateNaiveMin = new int[queries.length];
        int[] errorNaiveMin = new int[queries.length];

        long totalQtime = System.currentTimeMillis();
        int[] queryTime = new int[queries.length];
        for (int i = 0; i < queries.length; i++) {
            start = System.currentTimeMillis();
            estimateNaiveMin[i]= nm.query(queries[i]);
            end = System.currentTimeMillis();
            queryTime[i] = (int) (end - start);
            errorNaiveMin[i] = estimateNaiveMin[i] - exactAnswer[i];
//            if (errorNaiveMin[i] < 0) {
//                throw new RuntimeException("Error in Naive Min 2D hash");
//            }
//            System.out.println("Query " + i + ": estimate: " + estimateNaiveMin[i] + " exact: " + exactAnswer[i] + " error: " + errorNaiveMin[i]);
        }
        long end_totalQtime = System.currentTimeMillis();
        int totQtime = (int) (end_totalQtime - totalQtime);
//        System.out.println("Naive Min query time: " + (totQtime) + " ms");
        int sum_marg_qtime = 0;
        for (int i = 0; i < queries.length; i++) {
            sum_marg_qtime += queryTime[i];
        }
//        System.out.println("Naive Min query time sum: " + (sum_marg_qtime) + " ms");

        stats(exactAnswer, estimateNaiveMin, errorNaiveMin);
        int memUsage = nm.memoryUsage();
        writeResults(nm.setting, estimateNaiveMin, exactAnswer, errorNaiveMin, memUsage,ingestTime, queryTime, nm.getd(), nm.getw(), nm.ram);
    }

    private static void writeResults(String setting, int[] estimates, int[] exactAnswers, int[] errors, int memUsage, int ingestTime, int[] queryTime, int d, int w, int ram) throws IOException {
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

    private static void runRS(double[][] corr, int[][] data, int[][] queries, int[] exactAnswer, int ram) {
        ReservoirSample rs = new ReservoirSample(ram, corr.length);
        for (int[] datum : data) {
            rs.add(datum);
        }

        int[] estimateRS = new int[queries.length];
        int[] errorRS = new int[queries.length];


        for (int i = 0; i < queries.length; i++) {
            estimateRS[i]= rs.query(queries[i]);
            errorRS[i] = estimateRS[i] - exactAnswer[i];
        }

        stats(exactAnswer, estimateRS, errorRS);
        rs.memoryUsage();
    }

    private static void stats(int[] exactAnswer, int[] estimateRS, int[] errorRS) {


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
        double sum=0;
        double variance=0;
        for (double err : error) {
            sum+=err;
            variance+=err*err;
        }

        return new double[]{sum / error.length, sum, variance/error.length};

    }
}