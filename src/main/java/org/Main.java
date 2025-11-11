package org;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {

        int[] cardinalities = {10000,10000, 10000, 10000};

        double[][] corr = {
                {1.0, 0.9, 0.4, 0.2},
                {0.9, 1.0, 0.7, 0.1},
                {0.4, 0.7, 1.0, 0.6},
                {0.2, 0.1, 0.6, 1.0}
        };
//
//        double[][] corr = {
//                {1.0, 0.0},
//                {0.0, 1.0}
//        };
        CorrelatedDataGenerator generator = new CorrelatedDataGenerator(10000000, cardinalities, corr);
//        generator.generateDatasetNormal();
        generator.generateDatasetSkewed();
        generator.generateQueries(500);

        int[][] data = generator.getDataset();
        printSampleData(data);
        computeExactMutualInformation(data);
        int[][] queries = generator.getQueries();
        int[] exactAnswer = generator.getExactAnswer();

        printSampleQueries(queries, exactAnswer);
        runRS(corr, data, queries, exactAnswer, 5488);
        runChowMin(corr, data, queries, exactAnswer);
    }

    private static void computeExactMutualInformation(int[][] data) {
        //todo
    }

    private static void printSampleData(int[][] data) {
        System.out.println("Sample data:");
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < data[i].length; j++) {
                System.out.print(data[i][j] + " ");
            }
            System.out.println();
        }
    }

    private static void printSampleQueries(int[][] queries, int[] exactAnswer) {
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < queries[i].length; j++) {
                System.out.print(queries[i][j] + " ");
            }
            System.out.print(" exact: " + exactAnswer[i] + " ");
            System.out.println();
        }
    }

    private static void runChowMin(double[][] corr, int[][] data, int[][] queries, int[] exactAnswer) {
        ChowMin cm = new ChowMin(corr.length, corr);
        for (int[] datum : data) {
            cm.add(datum);
        }

        int[] estimateCM = new int[queries.length];
        int[] errorCM = new int[queries.length];


        for (int i = 0; i < queries.length; i++) {
            estimateCM[i]= cm.query(queries[i]);
            errorCM[i] = estimateCM[i] - exactAnswer[i];
            System.out.println("Estimate CM: " + estimateCM[i] + " error CM: " + errorCM[i]);
        }

        stats(exactAnswer, estimateCM, errorCM);
        cm.memoryUsage();
    }

    private static void runRS(double[][] corr, int[][] data, int[][] queries, int[] exactAnswer, int ram) {
        int size = ram / (4 * corr.length);
        ReservoirSample rs = new ReservoirSample(size, corr.length);
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