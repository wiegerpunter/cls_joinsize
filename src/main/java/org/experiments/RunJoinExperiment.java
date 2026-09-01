package org.experiments;

import com.opencsv.CSVWriter;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import org.dataset.JoinDataset;
import org.dataset.Query;
import org.dataset.SynopsisQuery;
import org.duckdb.DuckDBConnection;
import org.synopses.ChowMin.CMJoin;
import org.synopses.ChowMin.FilteredTable;
import org.synopses.RS.RSJoin;
import org.utils.Config;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class RunJoinExperiment {
    Config config;
    DuckDBConnection con;
    JoinDataset d;
    private final String folder = "/home/wieger/sandboxChowMin/resultsJoinSize/";
    private final String CSV_FILE_NAME = folder + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + ".csv";

    public RunJoinExperiment(Config config, JoinDataset d, DuckDBConnection con) throws Exception {
        this.config = config;
        this.d = d;
        this.con = con;

//        for (int ram : config.ramVals) {
//            System.out.println("Starting iteration over " + ram / 1000000 + " MB");
//            RSJoin rsj = new RSJoin();
//            rsj.initialize(d.tableNames, d.attrMap, ram, d.numColumnsPerTable, config);
//            rsj.ingestData(d, con);
//            queryRS(rsj);
//        }
        HashMap<String, Integer> joinKeyWidthMap = new HashMap<>();
        for (String jk : d.globalJoinKeyToTableColumn.keySet()) {
            joinKeyWidthMap.put(jk, config.joinWidth);
        }
        HashMap<String, HashMap<String, Integer>> widthMap = readWidthMaps(config.cardinalityMapPath, joinKeyWidthMap, config.maxLocalWidth);

        HashMap<String, HashMap<String, List<String>>> hhValuesMaps = null;
        if (config.sepHHs) {
            hhValuesMaps = readhhValuesMaps(config.hhValuesMapPath);
        }
        for (int j = 0; j < config.num_rows.length; j++) {
            CMJoin cmj = new CMJoin();
            long ingestTime = cmj.initialize(d, d.attrMap, 0, config.num_rows[j], widthMap, joinKeyWidthMap, config.sepHHs, hhValuesMaps, con);
            queryCM(cmj, ingestTime);
        }

    }

    private HashMap<String, HashMap<String, Integer>> readWidthMaps(String cardinalityMapPath, HashMap<String, Integer> joinKeyWidthMap, int maxLocalWidth) {
        HashMap<String, HashMap<String, Integer>> cardMap = readCardMaps(cardinalityMapPath);
        HashMap<String, HashMap<String, Integer>> widthMap = new HashMap<>();

        for (String table : cardMap.keySet()) {
            HashMap<String, Integer> cardMapTable = cardMap.get(table);
            for (String orgColName : cardMapTable.keySet()) {
                String colName = getColName(table, orgColName, d.globalColToJoinKey);
                if (joinKeyWidthMap.containsKey(colName)) {
                    widthMap.computeIfAbsent(table, k-> new HashMap<>()).put(orgColName, joinKeyWidthMap.get(colName));
                    System.out.println("Table " + table + " column " + orgColName + " width: " +joinKeyWidthMap.get(colName));
                } else {
                    int card = cardMap.get(table).get(colName);

                    widthMap.computeIfAbsent(table, k-> new HashMap<>()).put(colName,Math.min(maxLocalWidth, card));
                    System.out.println("Table " + table + " column " + colName + " width: " +Math.min(maxLocalWidth, card));

                }
            }
        }

        return widthMap;
    }

    private String getColName(String table, String orgColName, Map<SynopsisQuery.ColumnKey, String> globalColToJoinKey) {
        SynopsisQuery.ColumnKey c = new SynopsisQuery.ColumnKey(table, orgColName);
        return globalColToJoinKey.getOrDefault(c, orgColName);
    }

    private HashMap<String, HashMap<String, Integer>> readCardMaps(String path) {
        HashMap<String, HashMap<String, Integer>> cardinalityMap =new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(path))) {

            String line = br.readLine(); // header

            while ((line = br.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                String[] parts = line.split(",", -1);

                if (parts.length < 3) {
                    continue;
                }

                String table = parts[0].trim();
                String column = parts[1].trim();
                String cardString = parts[2].trim();
                int card = Integer.parseInt(cardString);

                cardinalityMap
                        .computeIfAbsent(table,
                                k -> new HashMap<>()).computeIfAbsent(column, k->card);


            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to read Card file: " + path, e);
        }

        return cardinalityMap;
    }

    private HashMap<String, HashMap<String,List<String>>> readhhValuesMaps(String hhValuesMapPath) {

        HashMap<String, HashMap<String,List<String>>> hhValuesMaps = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(hhValuesMapPath))) {

            String line = br.readLine(); // header

            while ((line = br.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                String[] parts = line.split(",", -1);

                if (parts.length < 4) {
                    continue;
                }

                String table = parts[0].trim();
                String column = parts[1].trim();
                String value = parts[2].trim();
                String count = parts[3].trim();

                hhValuesMaps
                        .computeIfAbsent(table,
                                k -> new HashMap<>()).computeIfAbsent(column,
                                k->new ArrayList<String>()).add(value);


            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to read HH file: " + hhValuesMapPath, e);
        }

        return hhValuesMaps;
    }

    private void queryRS(RSJoin rsj) {
        // execute queries in d on reservoir samples
        for (Query q : d.getQueries()) {
            int estimate = rsj.query(q.syn_query);
            double error = estimate - q.getResult();
            System.out.println("Query " + q.getName() + " error: " + error + " exact: " + q.getResult() + " estimate: " +estimate);
        }

    }
    private void queryCM(CMJoin cmj, long ingestTime) throws Exception {
        // execute queries in d on reservoir samples
        int numQueries = d.getQueries().length;
        String[] queryName = new String[numQueries];
        double[] estimates= new double[numQueries];
        double[] errors = new double[numQueries];
        double[] exact = new double[numQueries];
        double[] queryTime = new double[numQueries];
        int[] numJoins = new int[numQueries];
        double[][] tableNs = new double[numQueries][];
        int[][] tableJoinKeys = new int[numQueries][];
        String[][] tableNames = new String[numQueries][];
        List<SynopsisQuery.JoinPredicate>[] joins = new List[numQueries];
        List<EqualsTo>[] selections = new List[numQueries];
        for (int i = 0; i < numQueries; i++) {
            Query q = d.getQueries()[i];
            if (q.getName().equals("query_140_252.sql")) {
                System.out.println("debug");
            }
            CMJoin.QueryEst qEst = cmj.query(q.syn_query);
            double estimate = qEst.estimate();
            Map<String, FilteredTable> map = qEst.map();
            tableNs[i] = new double[map.size()];

            tableNames[i] = new String[map.size()];
            tableJoinKeys[i] = new int[map.size()];
            numJoins[i] = map.size();
            int j=0;
            for (FilteredTable ft : map.values()) {
                tableNames[i][j] = ft.t.getAlias().getName();
                tableNs[i][j] = ft.sumView;
                tableJoinKeys[i][j] = ft.unqColumns.size();
                j++;
            }
            double error = estimate - q.getResult();
            estimates[i] = estimate;
            errors[i] = error;
            exact[i] = q.getResult();
            queryName[i] = q.getName();
            joins[i] = q.syn_query.joins;
            selections[i] = q.syn_query.selections;
        }

        writeResults(cmj.setting, queryName, estimates, exact, errors, cmj.getDepth(), cmj.getMemory(),
                cmj.getDatasetMemory(), ingestTime, tableNs, tableNames,tableJoinKeys, numJoins, joins, selections);
    }

    private void writeResults(String setting,String[] queryName, double[] estimates, double[] exact,
                              double[] errors, int d, int ram, int datasetMemory,
                              long ingestTime, double[][] tableNs, String[][] tableNames, int[][] tableJoinKeys, int[] numTables,
                              List[] joins, List[] selections) throws Exception {


        boolean init = !new File(CSV_FILE_NAME).exists();

        CSVWriter writer = new CSVWriter(new FileWriter(CSV_FILE_NAME, true));
        if (init) {
            String[] header = new String[]{"queryID","setting","memUsage","estimate","exact","error","absError","relError",
                    "d","datasetMemory","ingestTime","tableNs","tableNames","tableJoinKeys","numTables","joins","selections"};
            writer.writeNext(header);
        }
        for  (int i = 0; i < estimates.length; i++) {
            String[] result = new String[17];
            result[0] = queryName[i];
            result[1] = setting;
            result[2] = String.valueOf(ram);
            result[3] = String.valueOf(estimates[i]);
            result[4] = String.valueOf(exact[i]);
            result[5] = String.valueOf(errors[i]);
            result[6] = String.valueOf(Math.abs(estimates[i] - exact[i]));
            result[7] = String.valueOf(Math.abs(estimates[i] - exact[i])/exact[i]);
            result[8] = String.valueOf(d);
            result[9] = String.valueOf(datasetMemory);
            result[10] = String.valueOf(ingestTime);
            result[11] = Arrays.toString(tableNs[i]);
            result[12] = Arrays.toString(tableNames[i]);
            result[13] = Arrays.toString(tableJoinKeys[i]);
            result[14] = String.valueOf(numTables[i]);
            result[15] = String.valueOf(joins[i]);
            result[16] = String.valueOf(selections[i]);

            writer.writeNext(result);
        }
        writer.close();

    }


}