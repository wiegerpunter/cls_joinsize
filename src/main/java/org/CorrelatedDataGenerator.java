package org;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

public class CorrelatedDataGenerator {
    private final int numRows;
    private final int numCols;
    private final int[] cardinalities;
    private final double[][] corrMatrix;
    private final Random random = new Random(0);

    private final double[][] latentData;
    private final int[][] dataset;
    private int[][] queries;
    private int[] exactAnswer;

    public CorrelatedDataGenerator(int numRows, int[] cardinalities, double[][] corrMatrix) {
        this.numRows = numRows;
        this.numCols = cardinalities.length;
        this.cardinalities = cardinalities;
        this.corrMatrix = corrMatrix;
        this.latentData = new double[numRows][numCols];
        this.dataset = new int[numRows][numCols];
    }

    public void generateDatasetNormal() {
        double[][] L = choleskyDecomposition(corrMatrix);

        // Step 1: generate correlated latent normal variables
        for (int i = 0; i < numRows; i++) {
            double[] z = new double[numCols];
            for (int j = 0; j < numCols; j++) {
                z[j] = random.nextGaussian();
            }

            for (int row = 0; row < numCols; row++) {
                double val = 0;
                for (int col = 0; col <= row; col++) {
                    val += L[row][col] * z[col];
                }
                latentData[i][row] = val;
            }
        }

        // Step 2: discretize based on cardinality per column
        for (int col = 0; col < numCols; col++) {
            double[] columnValues = new double[numRows];
            for (int row = 0; row < numRows; row++) {
                columnValues[row] = latentData[row][col];
            }

            // sort to assign discrete bins
            int[] indices = sortIndices(columnValues);
            int uniqueValues = cardinalities[col];
            for (int rank = 0; rank < numRows; rank++) {
                int category = (int) ((double) rank / numRows * uniqueValues);
                dataset[indices[rank]][col] = category;
            }
        }
    }

    public void generateDatasetSkewed() {
        double[][] L = choleskyDecomposition(corrMatrix);

        for (int i = 0; i < numRows; i++) {
            double[] z = new double[numCols];
            for (int j = 0; j < numCols; j++) {
                z[j] = random.nextGaussian();
            }

            // Apply Cholesky to get correlated normals
            double[] latent = new double[numCols];
            for (int row = 0; row < numCols; row++) {
                double val = 0;
                for (int col = 0; col <= row; col++) {
                    val += L[row][col] * z[col];
                }
                latent[row] = val;
            }

            // Transform to correlated Uniform(0,1)
            double[] u = new double[numCols];
            for (int j = 0; j < numCols; j++) {
                u[j] = normalCDF(latent[j]);
            }

            // Transform to desired skewed marginals
            for (int j = 0; j < numCols; j++) {
                dataset[i][j] = inverseSkewedCDF(u[j], j);
                // Each column j can have its own skewed distribution
            }
        }
    }

    private int inverseSkewedCDF(double u, int col) {
        return (int) - Math.log(1 - u);              // Exponential(1)]
    }

    private double normalCDF(double u) {
        return Math.exp(u);
    }


    private double[][] choleskyDecomposition(double[][] matrix) {
        int n = matrix.length;
        double[][] L = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = 0.0;
                for (int k = 0; k < j; k++) {
                    sum += L[i][k] * L[j][k];
                }
                if (i == j) {
                    L[i][j] = Math.sqrt(matrix[i][i] - sum);
                } else {
                    L[i][j] = (matrix[i][j] - sum) / L[j][j];
                }
            }
        }
        return L;
    }

    private int[] sortIndices(double[] values) {
        Integer[] idx = new Integer[values.length];
        for (int i = 0; i < values.length; i++) idx[i] = i;
        Arrays.sort(idx, Comparator.comparingDouble(i -> values[i]));
        int[] result = new int[values.length];
        for (int i = 0; i < values.length; i++) result[i] = idx[i];
        return result;
    }

    public int[][] getDataset() {
        return dataset;
    }

    public static void main(String[] args) {
        int[] cardinalities = {5, 10, 3, 8};

        // Example correlation matrix
        double[][] corr = {
                {1.0, 0.7, 0.4, 0.2},
                {0.7, 1.0, 0.5, 0.3},
                {0.4, 0.5, 1.0, 0.6},
                {0.2, 0.3, 0.6, 1.0}
        };
        CorrelatedDataGenerator generator = new CorrelatedDataGenerator(10, cardinalities, corr);
        generator.generateDatasetNormal();

        int[][] data = generator.getDataset();
        for (int[] row : data) {
            for (double v : row) {
                System.out.printf("%.4f ", v);
            }
            System.out.println();
        }


    }

    public void generateQueries(int numQueries) {
        queries = new int[numQueries][];
        int numQueriesGenerated = 0;
        for (int i = 0; i < dataset.length; i++) {
            if (i % 5 == 0 && numQueriesGenerated < numQueries) {
                // check if dataset[i] is in queries already, if so, skip:
                boolean alreadySeen = false;
                for (int[] row : queries) {
                    if(Arrays.equals(dataset[i], row)) {
                        alreadySeen = true;
                        break;
                    }
                }
                if (!alreadySeen) {

                    queries[numQueriesGenerated] = dataset[i];
                    numQueriesGenerated++;
                }
                // TODO: mask some attributes, arbitrary predicates.

            }
        }

        // exact answer:
        exactAnswer = new int[numQueries];
        for (int i = 0; i < numQueries; i++) {
            int[] query = queries[i];
            for (int[] record : dataset) {
                boolean found = true;
                for (int k = 0; k < query.length; k++) {
                    if (query[k] != record[k]) {
                        found = false;
                        break;
                    }
                }
                if (found) {
                    exactAnswer[i] += 1;
                }
            }
        }
    }

    public void generatePairwiseQueries(int numQueries) {
//        queries = new int[numQueries][numCols];
        ArrayList<int[]> potQueries = new ArrayList<>();
        int numQueriesGenerated = 0;

        Random random = new Random(1);
        for (int i = 0; i < dataset.length; i++) {
            if (i % 5 == 0 && numQueriesGenerated < numQueries) {
                // check if dataset[i] is in queries already, if so, skip:
                ArrayList<Integer> predAttrs = new ArrayList<>();
                predAttrs.add(random.nextInt(numCols));
                int secAttr;
                do {
                    secAttr = random.nextInt(numCols);
                } while (predAttrs.get(0) == secAttr);
                predAttrs.add(secAttr);
                // sort predAttrs
                predAttrs.sort(Comparator.naturalOrder());
                int[] query = new int[numQueries];
                query = Arrays.copyOf(dataset[i], dataset[i].length);
                // mask attributes
                for (int k = 0; k < query.length; k++) {
                    if (!predAttrs.contains(k)) {
                        query[k] = -1;
                    }
                }
                boolean alreadySeen = false;
                for (int[] row : potQueries) {
                    if(Arrays.equals(query, row)) {
                        alreadySeen = true;
                        break;
                    }
                }
                if (!alreadySeen) {
                    // mask attributes
                    potQueries.add(query);
//                    potQueries.add(Arrays.copyOf(dataset[i], dataset[i].length));
//                    for (int j = 0; j < dataset[i].length; j++) {
//                        // check if j in twoAttributes, if not, mask with -1
//                        boolean mask = true;
//                        for (int twoAttribute : twoAttributes) {
//                            if (twoAttribute == j) {
//                                mask = false;
//                                break;
//                            }
//                        }
//                        if (mask) {
//                            potQueries.get(numQueriesGenerated)[j] = -1;
//                        }
//                    }
                    numQueriesGenerated++;
                }
            }
        }

        queries = new int[numQueriesGenerated][];
        for (int i = 0; i < numQueriesGenerated; i++) {
            queries[i] = potQueries.get(i);
        }

        // exact answer:
        exactAnswer = new int[queries.length];
        for (int i = 0; i < queries.length; i++) {
            int[] query = queries[i];
            for (int[] record : dataset) {
                boolean found = true;
                for (int k = 0; k < query.length; k++) {
                    if (query[k] != -1) {
                        if (query[k] != record[k]) {
                            found = false;
                            break;
                        }
                    }
                }
                if (found) {
                    exactAnswer[i] += 1;
                }
            }
        }
    }

    public int[] getExactAnswer() {
        return exactAnswer;
    }
    public int[][] getQueries() {
        return queries;
    }
    public int[][] getPairwiseQueries() {
        return queries;
    }
}
