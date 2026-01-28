package org.dataset;

import org.dataset.DependencyModels.*;
import org.dataset.Distributions.ZipfDistribution;

import java.util.*;
import java.util.stream.IntStream;

public class DatasetGenerator {
    private final DependencyModel model;
    private final int numAttrs;
    private final int numRecords;

    public DatasetGenerator(DependencyModel model, int numAttrs, int numRecords) {
        this.model = model;
        this.numAttrs = numAttrs;
        this.numRecords = numRecords;
    }

    public int[][] generate(Random rng) {
        int[][] data = new int[numRecords][numAttrs];
        for (int i = 0; i < numRecords; i++) {
            model.generateRecord(data[i], rng);
        }
        return data;
    }

    public static int sampleCardinality(Random rng) {
        double p = rng.nextDouble();
        if (p < 0.5) return rng.nextBoolean() ? 2 : 3;
        if (p < 0.8) return 5 + rng.nextInt(16);
        if (p < 0.95) return 50 + rng.nextInt(150);
        return 1000 + rng.nextInt(9000);
    }

    public static int[][] getData(int numAttrs, int N, int seed, String modelType) {
        Random rng = new Random(seed);

        Attribute[] attrs = new Attribute[numAttrs];
        for (int i = 0; i < numAttrs; i++) {
            int k = sampleCardinality(rng);
            attrs[i] = new Attribute(i, k, new ZipfDistribution(k, 1.2));
        }

        int[][] clusters = { {0,1,2}, {4,5} };

        DependencyModel model;
        switch (modelType) {
            case "IndependentModel" -> model = new IndependentModel(attrs);
            case "ClusteredModel" -> model = new ClusteredModel(attrs, clusters, 0.15);
            case "FunctionalDependencyModel" -> model = new FunctionalDependencyModel(attrs, 0,
                    IntStream.range(1,attrs.length).toArray());
            case "LatentFactorModel" -> model = new LatentFactorModel(attrs, 10, 0.5);
            default -> throw new RuntimeException("Model type not found.");
        } 

        DatasetGenerator gen =
                new DatasetGenerator(model, numAttrs, N);

        return gen.generate(rng);
    }

    public static class Queries {
        int[][] queries;
        int[] exactAnswer;
        public Queries(int[][] queries, int[] exactAnswer){
            this.queries= queries;
            this.exactAnswer = exactAnswer;
        }

        public int[][] getQueries() {
            return queries;
        }
        public int[] getExactAnswer() {
            return exactAnswer;
        }
    }

    public static Queries genQueries(int numQueriesPerPredicateNumber, int numPredicates, int[][] dataset) {
//        queries = new int[numQueries][numCols];
        ArrayList<int[]> potQueries = new ArrayList<>();
        int numQueriesGenerated = 0;
        int numAttributes = dataset[0].length;
        for (int p = 1; p <= numPredicates; p++) {
            numQueriesGenerated += addPotQueries(p, numQueriesPerPredicateNumber, potQueries, dataset, numAttributes);
        }
        int[][] queries = new int[numQueriesGenerated][];
        for (int i = 0; i < numQueriesGenerated; i++) {
            queries[i] = potQueries.get(i);
        }

        // exact answer:
        int[] exactAnswer = new int[queries.length];
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
        return new Queries(queries, exactAnswer);
    }


    private static int addPotQueries(int p, int numQueries, ArrayList<int[]> potQueries,
                                     int[][] dataset, int numAttributes) {
        int numQueriesGenerated = 0;
        Random r = new Random(p);
        Random rAttrs =new Random(0);
        for (int[] ints : dataset) {
            double chance = r.nextDouble(1);
            if (chance < 0.2 && numQueriesGenerated < numQueries) {
                // check if dataset[i] is in queries already, if so, skip:
                Set<Integer> predAttrs = new HashSet<>();
                while (predAttrs.size() < p) {
                    predAttrs.add(rAttrs.nextInt(numAttributes));
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
//
//    public static Queries genQueries(int numQueries, int[][] dataset) {
//        int[][] queries = new int[numQueries][];
//        int numQueriesGenerated = 0;
//        for (int i = 0; i < dataset.length; i++) {
//            if (i % 5 == 0 && numQueriesGenerated < numQueries) {
//                // check if dataset[i] is in queries already, if so, skip:
//                boolean alreadySeen = false;
//                for (int[] row : queries) {
//                    if(Arrays.equals(dataset[i], row)) {
//                        alreadySeen = true;
//                        break;
//                    }
//                }
//                if (!alreadySeen) {
//
//                    queries[numQueriesGenerated] = dataset[i];
//                    numQueriesGenerated++;
//                }
//
//            }
//        }
//
//        // exact answer:
//        int[] exactAnswer = new int[numQueries];
//        for (int i = 0; i < numQueries; i++) {
//            int[] query = queries[i];
//            for (int[] record : dataset) {
//                boolean found = true;
//                for (int k = 0; k < query.length; k++) {
//                    if (query[k] != record[k]) {
//                        found = false;
//                        break;
//                    }
//                }
//                if (found) {
//                    exactAnswer[i] += 1;
//                }
//            }
//        }
//
//        Queries q = new Queries(queries, exactAnswer);
//        return q;
//    }

}


