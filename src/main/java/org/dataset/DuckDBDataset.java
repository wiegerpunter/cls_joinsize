package org.dataset;

import java.sql.*;
import java.util.*;

import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;

public class DuckDBDataset {

    public static DuckDBConnection createInMemoryDB(int[][] dataset) throws SQLException {
        DuckDBConnection conn = (DuckDBConnection) DriverManager.getConnection("jdbc:duckdb:");

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

        DuckDBAppender appender = conn.createAppender("main", "dataset");
        for (int[] row : dataset) {
            appender.beginRow();
            for (int i = 0; i < numAttributes; i++) {
                appender.append(row[i]);
            }
            appender.endRow();
        }

        appender.close();
        return conn;
    }
}
