package org.dataset;

import org.utils.Config;
import org.utils.Record;

import java.util.*;
import java.util.stream.Collectors;

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

    public void generatePairwiseQueries(int numQueriesPerPredicateNumber, int numPredicates) {
//        queries = new int[numQueries][numCols];
        ArrayList<int[]> potQueries = new ArrayList<>();
        int numQueriesGenerated = 0;
        for (int p = 1; p <= numPredicates; p++) {
            numQueriesGenerated += addPotQueries(p, numQueriesPerPredicateNumber, potQueries);
        }
        queries = new int[numQueriesGenerated][];
        for (int i = 0; i < numQueriesGenerated; i++) {
            queries[i] = potQueries.get(i);
        }

        // exact answer:
        exactAnswer = new int[queries.length];
        for (int i = 0; i < queries.length; i++) {
            for (int[] record : dataset) {
                boolean found = true;
                for (int k = 0; k < queries[i].length; k++) {
                    if (queries[i][k] != -1 && queries[i][k] != record[k]) {
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

    private int addPotQueries(int p, int numQueries, ArrayList<int[]> potQueries) {
        int numQueriesGenerated = 0;
        Random r = new Random(p);
        for (int[] ints : dataset) {
            double chance = r.nextDouble(1);
            if (chance < 0.2 && numQueriesGenerated < numQueries) {
                // check if dataset[i] is in queries already, if so, skip:
                Set<Integer> predAttrs = new HashSet<>();
                while (predAttrs.size() < p) {
                    predAttrs.add(random.nextInt(numCols));
                }
                int[] query = Arrays.copyOf(ints, ints.length);

                // mask non-predicates
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
                    potQueries.add(query);
                    numQueriesGenerated++;
                }
            }
        }
        return numQueriesGenerated;

    }

//    public void generatePredQueries(int numQueries) {
//        queries = new int[numQueries][numCols];
//        ArrayList<int[]> potQueries = new ArrayList<>();
//        int numQueriesGenerated = 0;
//
//        Random random = new Random(1);
//        for (int i = 0; i < dataset.length; i++) {
//            if (i % 5 == 0 && numQueriesGenerated < numQueries) {
//                // check if dataset[i] is in queries already, if so, skip:
//                ArrayList<Integer> predAttrs = new ArrayList<>();
//                predAttrs.add(random.nextInt(numCols));
//                int secAttr;
//                do {
//                    secAttr = random.nextInt(numCols);
//                } while (predAttrs.get(0) == secAttr);
//                predAttrs.add(secAttr);
//
//                int thirdAttr;
//                do {
//                    thirdAttr = random.nextInt(numCols);
//                } while (predAttrs.get(0) == thirdAttr || predAttrs.get(1) == thirdAttr);
//                predAttrs.add(thirdAttr);
//                // sort predAttrs
//                predAttrs.sort(Comparator.naturalOrder());
//                int[] query = new int[numQueries];
//                query = Arrays.copyOf(dataset[i], dataset[i].length);
//                // mask attributes
//                for (int k = 0; k < query.length; k++) {
//                    if (!predAttrs.contains(k)) {
//                        query[k] = -1;
//                    }
//                }
//                boolean alreadySeen = false;
//                for (int[] row : potQueries) {
//                    if(Arrays.equals(query, row)) {
//                        alreadySeen = true;
//                        break;
//                    }
//                }
//                if (!alreadySeen) {
//                    // mask attributes
//                    potQueries.add(query);
//                    numQueriesGenerated++;
//                }
//            }
//        }
//
//        queries = new int[numQueriesGenerated][];
//        for (int i = 0; i < numQueriesGenerated; i++) {
//            queries[i] = potQueries.get(i);
//        }
//
//        // exact answer:
//        exactAnswer = new int[queries.length];
//        for (int i = 0; i < queries.length; i++) {
//            int[] query = queries[i];
//            for (int[] record : dataset) {
//                boolean found = true;
//                for (int k = 0; k < query.length; k++) {
//                    if (query[k] != -1) {
//                        if (query[k] != record[k]) {
//                            found = false;
//                            break;
//                        }
//                    }
//                }
//                if (found) {
//                    exactAnswer[i] += 1;
//                }
//            }
//        }
//    }

    public int[] getExactAnswer() {
        return exactAnswer;
    }
    public int[][] getQueries() {
        return queries;
    }

    public void run(Config config) {
        switch (config.dataDistribution) {
            case "Uniform" -> this.generateDatasetUniform();
            case "PerfectFD" -> this.generateDataPerfectFD();
            case "Corr" -> this.generateDataCorr();
            default -> throw new RuntimeException("Data distribution not recognized");
        }

        // Generate queries
        this.generatePairwiseQueries(config.numQueries, numCols);
//        this.generatePredQueries(500);

        if (config.statsData) {
            statsData(dataset);
        }
        if (config.printData) {
            printSampleData(dataset);
        }

    }


    private static void statsData(int[][] data) {
        // cardinality per attribute
        // frequency for top-10 values

        HashMap<org.utils.Record, Integer> uniqueRecords = new HashMap<>();
        HashMap<Integer, Integer>[] uniqueRecsPerAttr= new HashMap[data[0].length];
        for (int i = 0; i < data[0].length; i++) {
            uniqueRecsPerAttr[i] = new HashMap<>();
        }
        for (int[] datum : data) {
            org.utils.Record r = new org.utils.Record(datum);
            uniqueRecords.put(r, uniqueRecords.getOrDefault(r, 0) + 1);
            for (int attr = 0; attr < datum.length; attr++) {
                uniqueRecsPerAttr[attr].put(datum[attr], uniqueRecords.getOrDefault(r, 0) + 1);
            }
        }

        int K = 10;
        // freq topk vals
        List<Map.Entry<org.utils.Record, Integer>> topKByValue = topKByValue(uniqueRecords, K);

        for (Map.Entry<Record, Integer> entry : topKByValue) {
            System.out.println(Arrays.toString(entry.getKey().getRec()) + " : " + entry.getValue());

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
                .sorted((a, b) -> Integer.compare(b.getKey().getRec()[attrIndex], a.getKey().getRec()[attrIndex])) // descending by rec[i]
                .limit(k)
                .collect(Collectors.toList());
    }


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

}
