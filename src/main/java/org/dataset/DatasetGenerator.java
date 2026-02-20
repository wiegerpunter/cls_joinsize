package org.dataset;

import org.dataset.DependencyModels.*;
import org.dataset.Distributions.ZipfDistribution;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;
import org.utils.Config;

import java.sql.*;
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

    public static int[][] getData(Config config, int numAttributes) {
        Random rng = new Random(config.seed);

        Attribute[] attrs = new Attribute[numAttributes];
        for (int i = 0; i < numAttributes; i++) {
            int k = sampleCardinality(rng);
            attrs[i] = new Attribute(i, k, new ZipfDistribution(k, 1.5));
        }

        DependencyModel model;
        switch (config.modelType) {
            case "IndependentModel" -> model = new IndependentModel(attrs);
            case "ClusteredModel" -> model = new ClusteredModel(attrs, config.clusters, config.clusterNoise);
            case "FunctionalDependencyModel" -> model = new FunctionalDependencyModel(attrs, 0,
                    IntStream.range(1,attrs.length).toArray());
            case "LatentFactorModel" -> model = new LatentFactorModel(attrs, config.latentCard, config.latentInfl);
            default -> throw new RuntimeException("Model type not found.");
        } 

        DatasetGenerator gen =
                new DatasetGenerator(model, numAttributes, config.N);

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

    public static Queries genQueries(int numQueriesPerPredicateNumber, int numPredicates, int[][] dataset) throws SQLException {
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
        try (DuckDBConnection conn = (DuckDBConnection) DriverManager.getConnection("jdbc:duckdb:")) {
            createDatasetTable(conn, dataset);
            groupDatasetTable(conn, dataset);
            createQueriesTable(conn, queries);


            int[] exactAnswer = computeAllCounts(conn, queries.length, numAttributes);

            return new Queries(queries, exactAnswer);
        }
    }

    private static void groupDatasetTable(DuckDBConnection conn, int[][] dataset) throws SQLException {
        int numAttributes = dataset[0].length;
        StringBuilder createSQL = new StringBuilder("CREATE TABLE dataset_grouped AS SELECT ");
        for (int i = 0; i < numAttributes; i++) {
            if (i > 0) createSQL.append(", ");
            createSQL.append("a").append(i);
        }
        createSQL.append(", COUNT(*) AS cnt FROM dataset GROUP BY ALL;");
        try (Statement stmt = conn.createStatement()) {
            System.out.println("SQL StringL: " + createSQL);
            stmt.execute(createSQL.toString());
        }
    }

    private static void createDatasetTable(DuckDBConnection conn, int[][] dataset) throws SQLException {
        int numAttributes = dataset[0].length;

        // Create table schema dynamically
        StringBuilder createSQL = new StringBuilder("CREATE TABLE dataset (");
        for (int i = 0; i < numAttributes; i++) {
            if (i > 0) createSQL.append(", ");
            createSQL.append("a").append(i).append(" INT");
        }
        createSQL.append(");");

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createSQL.toString());
        }

        DuckDBAppender appender = new DuckDBAppender(conn, "main", "dataset");
        for (int[] row : dataset) {
            appender.beginRow();
            for (int i = 0; i < numAttributes; i++) {
                appender.append(row[i]);
            }
            appender.endRow();
        }

        appender.close();
    }

    private static void createQueriesTable(Connection conn, int[][] queries) throws SQLException {
        int numAttributes = queries[0].length;

        StringBuilder createSQL = new StringBuilder("CREATE TABLE queries (qid INT, ");
        for (int i = 0; i < numAttributes; i++) {
            if (i > 0) createSQL.append(", ");
            createSQL.append("a").append(i).append(" INT");
        }
        createSQL.append(");");

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createSQL.toString());
        }

        DuckDBAppender appender = new DuckDBAppender((DuckDBConnection) conn, "main", "queries");

        for (int qid = 0; qid < queries.length; qid++) {
            appender.beginRow();
            appender.append(qid);
            for (int i = 0; i < numAttributes; i++) {
                appender.append(queries[qid][i]);
            }
            appender.endRow();
        }
        appender.close();


//        StringBuilder insertSQL = new StringBuilder("INSERT INTO queries VALUES (?");
//        for (int i = 0; i < numAttributes; i++) {
//            insertSQL.append(", ?");
//        }
//        insertSQL.append(");");
//
//        try (PreparedStatement ps = conn.prepareStatement(insertSQL.toString())) {
//            for (int qid = 0; qid < queries.length; qid++) {
//                ps.setInt(1, qid);
//                for (int i = 0; i < numAttributes; i++) {
//                    ps.setInt(i + 2, queries[qid][i]);
//                }
//                ps.addBatch();
//            }
//            ps.executeBatch();
//        }
    }

    private static int[] computeAllCounts(Connection conn, int numQueries, int numAttributes) throws SQLException {
        int[] counts = new int[numQueries];

        // Build join condition: (q.ai = -1 OR q.ai = d.ai)
        StringBuilder joinCond = new StringBuilder();
        for (int i = 0; i < numAttributes; i++) {
            if (i > 0) joinCond.append(" AND ");
            joinCond.append("(q.a").append(i).append(" = -1 OR q.a").append(i).append(" = d.a").append(i).append(")");
        }

        String sql =
                "SELECT q.qid, SUM(d.cnt) AS cnt " +
                        "FROM queries q " +
                        "JOIN dataset_grouped d ON " + joinCond +
                        "GROUP BY q.qid " +
                        "ORDER BY q.qid";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                int qid = rs.getInt("qid");
                int cnt = rs.getInt("cnt");
                counts[qid] = cnt;
            }
        }

        return counts;
    }


    private static int computeCount(DuckDBConnection conn, int[] query) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM dataset WHERE 1=1");
        ArrayList<Integer> params = new ArrayList<>();

        for (int i = 0; i < query.length; i++) {
            if (query[i] != -1) {
                sql.append(" AND a").append(i).append(" = ?");
                params.add(query[i]);
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setInt(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
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


