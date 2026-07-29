package org.synopses.RS;

import net.sf.jsqlparser.schema.Table;
import org.dataset.JoinDataset;
import org.dataset.SynopsisQuery;
import org.duckdb.DuckDBConnection;
import org.experiments.RunJoinExperiment;
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
            int numRecs = 0;
            try (Statement stmt = con.createStatement();
                 ResultSet rs = stmt.executeQuery(dataQuery);
            ) {
                while (rs.next()) {
                    String[] rec = new String[numColumns];
                    for (int col = 1; col <= numColumns; col++) {
                        rec[col - 1] = rs.getString(col);
//                        rec[col - 1] = "1";
                    }
                    syn.add(rec);
                    numRecs++;
//                    if (numRecs ==2) {
//                        break;
//                    }
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
        for (Table table : synQuery.tables) {
            ReservoirSampleString rsCopy = rsj.get(table.getName()).filtered(synQuery.getSelectionPredicatesPerTable(table));
            // apply local predicates

            querySample.put(table.getAlias().getName(), rsCopy);
            if (rsCopy.size() == 0) {
                return 0;
            }
            scale *= ((double) rsCopy.getCount() / rsCopy.size());
        }

        //3 compute join size based on synQuery.join
        long joined =
                computeJoinSize(
                        querySample,
                        synQuery.joins,
                        synQuery.aliasMap
                );
        return Math.toIntExact(Math.round(joined * scale));
    }

    private long computeJoinSize(
            Map<String, ReservoirSampleString> samples,
            List<SynopsisQuery.JoinPredicate> joins, HashMap<String, String> aliasMap
    ) {

        List<Map<String,String[]>> tuples =
                new ArrayList<>();

        //------------------------------------
        // initialize with first table that occurs in join
        //------------------------------------

        String first = samples.keySet()
                        .iterator()
                        .next();

        ReservoirSampleString r =  samples.get(first);
        for (int i = 0; i < r.size(); i++) {
            String[] row = r.reservoir[i];
            Map<String,String[]> x = new HashMap<>();
            x.put(first,row);
            tuples.add(x);
        }

        //------------------------------------
        // join remaining tables
        //------------------------------------

        for (String table : samples.keySet()) {
            if (table.equals(first)) continue;
            ReservoirSampleString rs = samples.get(table);
            List<Map<String,String[]>> next = new ArrayList<>();
            for (Map<String,String[]> partial : tuples) {
                for (int j = 0; j < rs.size(); j++) {
                    String[] candidate = rs.reservoir[j];
                    if ( satisfies(
                            partial,
                            table,
                            candidate,
                            joins,
                            samples,
                            aliasMap
                    ) ) {
                        Map<String,String[]> copy = new HashMap<>(partial);
                        copy.put(table, candidate);
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
            Map<String,String[]> partial,
            String newTable,
            String[] newRow,
            List<SynopsisQuery.JoinPredicate> joins,
            Map<String,ReservoirSampleString> samples,
            HashMap<String, String> aliasMap
    ) {

        for (SynopsisQuery.JoinPredicate j : joins) {

            boolean right =
                    j.right().getTableName().equals(newTable);

            boolean left =
                    j.left().getTableName().equals(newTable);
            if (!right && !left)
                continue;

            String existingTable = left ? j.right().getTableName() : j.left().getTableName();

            if (!partial.containsKey(existingTable)) {
                continue;
            }

            String[] existing = partial.get(existingTable);

            String existingCol = left ? j.right().getColumnName() : j.left().getColumnName();

            String newCol = left ? j.left().getColumnName() : j.right().getColumnName();

            int e =
                    samples.get(existingTable).getIdCol(existingCol);

            int n = samples.get(newTable).getIdCol(newCol);

            if (existing[e] == null) {
                return false;
            }
            if (!existing[e].equals(newRow[n])) {
                return false;
            }
        }

        return true;
    }

}
