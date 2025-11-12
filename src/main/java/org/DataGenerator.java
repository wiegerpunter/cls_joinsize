package org;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;

public class DataGenerator {
    private final int numRows;
    private final int numCols;
    private final int[] cardinalities;
    private final Random random = new Random(0);

    private final int[][] dataset;
    private int[][] queries;
    private int[] exactAnswer;

    public DataGenerator(int numRows, int[] cardinalities) {
        this.numRows = numRows;
        this.numCols = cardinalities.length;
        this.cardinalities = cardinalities;
        this.dataset = new int[numRows][numCols];
    }

    public void generateDatasetUniform() {
        for (int i = 0; i < numRows; i++) {
            int[] z = new int[numCols];
            for (int j = 0; j < numCols; j++) {
                z[j] = random.nextInt(cardinalities[j]);
            }
            dataset[i] = z;
        }
    }

    public void generateDataPerfectFD() {
        for (int i = 0; i < numRows; i++) {
            int[] z = new int[numCols];
            z[0] = random.nextInt(cardinalities[0]);
            for (int j = 0; j < numCols; j++) {
                z[j] = z[0];
            }
            dataset[i] = z;
        }
    }

    public void generateDataCorr() {
        Random r1 = new Random(1);
        for (int i = 0; i < numRows; i++) {
            int[] z = new int[numCols];
            z[0] = random.nextInt(cardinalities[0]);
            for (int j = 0; j < numCols; j++) {
                if ((r1.nextDouble(1) < 0.9 - j*0.15)) {
                    z[j] = random.nextInt(cardinalities[j]);
                } else {
                    z[j] = z[0];
                }
            }
            dataset[i] = z;
        }
    }

    public int[][] getDataset() {
        return dataset;
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
                    if (Arrays.equals(query, row)) {
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
    public void generatePredQueries(int numQueries) {
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

                int thirdAttr;
                do {
                    thirdAttr = random.nextInt(numCols);
                } while (predAttrs.get(0) == thirdAttr || predAttrs.get(1) == thirdAttr);
                predAttrs.add(thirdAttr);
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
