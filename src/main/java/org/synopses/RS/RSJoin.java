package org.synopses.RS;

import org.dataset.JoinDataset;
import org.dataset.SynopsisQuery;
import org.duckdb.DuckDBConnection;
import org.utils.Config;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RSJoin {
    HashMap<String, ReservoirSampleString> rsj;
    HashMap<String, String[]> attrMap;
    public RSJoin() {
        rsj = new HashMap<>();
        attrMap = new HashMap<>();
    }

    public void initialize(String[] tableNames, HashMap<String, String[]> attrMap, int ram, HashMap<String, Integer> numColumnsPerTable, Config config) {
        for (String t : tableNames) {
            rsj.put(t, new ReservoirSampleString(t, attrMap.get(t), ram, numColumnsPerTable.get(t), config.seed + 5));
        }
    }

    public void ingestData(JoinDataset d, DuckDBConnection con) throws SQLException {
        // per table, insert all data into right synopsis
        for (String t : d.tableNames) {
            String dataQuery = "SELECT * FROM " + t;
            ReservoirSampleString syn = rsj.get(t);
            int numColumns = d.numColumnsPerTable.get(t);
            try (Statement stmt = con.createStatement();
                 ResultSet rs = stmt.executeQuery(dataQuery);
            ) {
                while (rs.next()) {
                    String[] rec = new String[numColumns];
                    for (int col = 1; col <= numColumns; col++) {
                        rec[col - 1] = rs.getString(col);
                    }
                    syn.add(rec);
                }
            }
        }
        }

    public int query(SynopsisQuery synQuery) {
        // 1. select tables needed for query
        // 2. apply predicates on tables
        // 3. per join (possibly bad order), execute on samples
        HashMap<String, ReservoirSampleString> querySample = new HashMap<>();
        double scale = 1;
        for (String table : synQuery.tables) {
            ReservoirSampleString rsCopy = rsj.get(table).filtered(synQuery.getSelectionPredicatesPerTable(table));
            // apply local predicates

            querySample.put(table, rsCopy);
            if (rsCopy.size() == 0) {
                return 0;
            }
            scale *= ((double) rsCopy.getCount() / rsCopy.size());
        }

        //3 compute join size based on synQuery.join
        long joined =
                computeJoinSize(
                        querySample,
                        synQuery.joins
                );
        return Math.toIntExact(Math.round(joined * scale));
    }

    private long computeJoinSize(
            Map<String, ReservoirSampleString> samples,
            List<SynopsisQuery.JoinPredicate> joins
    ) {

        List<Map<String,String[]>> tuples =
                new ArrayList<>();

        //------------------------------------
        // initialize with first table
        //------------------------------------

        String first =
                samples.keySet()
                        .iterator()
                        .next();

        for (String[] row :
                samples.get(first).reservoir) {

            Map<String,String[]> x =
                    new HashMap<>();

            x.put(first,row);

            tuples.add(x);
        }

        //------------------------------------
        // join remaining tables
        //------------------------------------

        for (String table :
                samples.keySet()) {

            if (table.equals(first))
                continue;

            ReservoirSampleString rs =
                    samples.get(table);

            List<Map<String,String[]>>
                    next =
                    new ArrayList<>();

            for (Map<String,String[]> partial :
                    tuples) {

                for (String[] candidate :
                        rs.reservoir) {

                    if (
                            satisfies(
                                    partial,
                                    table,
                                    candidate,
                                    joins,
                                    samples
                            )
                    ) {

                        Map<String,String[]> copy =
                                new HashMap<>(partial);

                        copy.put(
                                table,
                                candidate
                        );

                        next.add(copy);
                    }
                }
            }

            tuples = next;

            if (tuples.isEmpty())
                return 0;
        }

        return tuples.size();
    }
    private boolean satisfies(
            Map<String,String[]> joined,
            String newTable,
            String[] newRow,
            List<SynopsisQuery.JoinPredicate> joins,
            Map<String,ReservoirSampleString> samples
    ) {

        for (SynopsisQuery.JoinPredicate j : joins) {

            boolean right =
                    j.rightTable()
                            .equals(newTable);

            boolean left =
                    j.leftTable()
                            .equals(newTable);

            if (!right && !left)
                continue;

            String existingTable =
                    left
                            ? j.rightTable()
                            : j.leftTable();

            if (!joined.containsKey(
                    existingTable
            )) {
                continue;
            }

            String[] existing =
                    joined.get(
                            existingTable
                    );

            String existingCol =
                    left
                            ? j.rightColumn()
                            : j.leftColumn();

            String newCol =
                    left
                            ? j.leftColumn()
                            : j.rightColumn();

            int e =
                    samples
                            .get(existingTable)
                            .getIdCol(existingCol);

            int n =
                    samples
                            .get(newTable)
                            .getIdCol(newCol);

            if (
                    !existing[e]
                            .equals(
                                    newRow[n]
                            )
            ) {
                return false;
            }
        }

        return true;
    }

}
