package org.experiments;

import com.opencsv.CSVWriter;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import org.dataset.JoinDataset;
import org.dataset.Query;
import org.dataset.SynopsisQuery;
import org.duckdb.DuckDBConnection;
import org.synopses.ChowMin.CMJoin;
import org.synopses.ChowMin.FilteredTable;
import org.synopses.factorJoin.FactorJoinASH;
import org.synopses.factorJoin.FilteredTableASH;
import org.synopses.factorJoin.aSH;
import org.utils.Config;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.*;

public class RunJoinExperiment {
    Config config;
    DuckDBConnection con;
    JoinDataset d;// = "/home/wieger/sandboxChowMin/resultsJoinSize/";
    private final String CSV_FILE_NAME;// = folder + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + ".csv";

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

//        String folder = config.outputFolder;//"/home/wieger/sandboxChowMin/resultsJoinSize/";
        Path folder = Paths.get(config.outputFolder);
        Files.createDirectories(folder);
        CSV_FILE_NAME = folder +"/seed_" + config.seed +"_"+ new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "_" + config.seed + ".csv";
        HashMap<String, Buckets> globalJoinKeyBins = readJoinKeyBins(config.binPath, config.joinWidth, d.globalColToJoinKey);


        HashMap<String, Integer> joinKeyWidthMap = new HashMap<>();
        for (String jk : d.globalJoinKeyToTableColumn.keySet()) {
            joinKeyWidthMap.put(jk, globalJoinKeyBins.get(jk).nbins);
        }
        HashMap<String, HashMap<String, HashMap<String, Integer>>> exactHashMap = new HashMap<>();
        HashMap<String, HashMap<String,Boolean>> exactHashable = new HashMap<>();
        HashMap<String, HashMap<String, Integer>> widthMap = readWidthMaps(config.cardinalityMapPath, joinKeyWidthMap, exactHashable,
                config.maxLocalWidth);


        setExactHashMaps(exactHashable, exactHashMap);

        HashMap<String, HashMap<String, List<String>>> hhValuesMaps = null;
        if (config.sepHHs) {
            hhValuesMaps = readhhValuesMaps(config.hhValuesMapPath);
        }
        long ingestTimeRead = readTime(d, con);
        writeReadTime(ingestTimeRead);
        System.out.println("Read time saved");

        long ram=0;
        for (int j = 0; j < config.num_rows.length; j++) {
            ram=0;
            CMJoin cmj = new CMJoin(config, d, d.attrMap, 0, config.num_rows[j], widthMap, exactHashable, exactHashMap,
                    joinKeyWidthMap, config.sepHHs, hhValuesMaps, config.seed);
            long ingestTime = cmj.initialize(con);
            System.out.println("Done with CLS ingestion");
            con.close();
            queryCM(cmj, ingestTime);
            ram +=  cmj.getMemory();
        }

//        ram = 64642368;// 43539456;
//        long ram = 8000000;
//        HashMap<String, Integer> parMap = getParMap(ram, d.attrMap);
//        FactorJoinRS fj = new FactorJoinRS(d, joinKeyWidthMap, parMap, d.attrMap, config.seed, globalJoinKeyBins);
//        long ingestTime = fj.ingest(con);
//        queryFJ(fj, ingestTime);
        Properties props = new Properties();
        props.setProperty("access_mode", "READ_ONLY");

        con =
                (DuckDBConnection) DriverManager.getConnection(
                        "jdbc:duckdb:" + config.duckdbPath,
                        props
                );
        double ingestBuffer = 0.1;
        HashMap<String, int[]> parMapASH = getParMapASH(ram, d.tableSizes, d.attrMap, d.jksPerTable, globalJoinKeyBins, ingestBuffer, config.uqRatio);
        FactorJoinASH fj = new FactorJoinASH(config, d, joinKeyWidthMap, parMapASH, d.attrMap, config.seed, globalJoinKeyBins);
        long ingestTime = fj.ingest(con);
        System.out.println("Done with FJ ingestion.");
        con.close();
        queryFJ(fj, ingestTime);

    }

    private void writeReadTime(long readIngestTime) throws Exception {
        int numQueries = d.getQueries().length;
        String[] queryName = new String[numQueries];
        double[] estimates= new double[numQueries];
        double[] errors = new double[numQueries];
        double[] exact = new double[numQueries];
        long[] queryTimes = new long[numQueries];
        int[] numJoins = new int[numQueries];
        double[][] tableNs = new double[numQueries][];
        int[][] tableJoinKeys = new int[numQueries][];
        String[][] tableNames = new String[numQueries][];
        List<SynopsisQuery.JoinPredicate>[] joins = new List[numQueries];
        List<EqualsTo>[] selections = new List[numQueries];
        for (int i = 0; i < numQueries; i++) {
            Query q = d.getQueries()[i];
            long start = System.currentTimeMillis();
            long end = System.currentTimeMillis();
            long queryTime = end - start;
            double estimate = 1;
            tableNs[i] = new double[0];

            tableNames[i] = new String[0];
            tableJoinKeys[i] = new int[0];
            numJoins[i] = 1;
            estimates[i] = estimate;
            errors[i] = 1;
            exact[i] = 1;
            queryName[i] = q.getName();
            joins[i] = q.syn_query.joins;
            selections[i] = q.syn_query.selections;
            queryTimes[i] = queryTime;
        }

        writeResults("readFile", queryName, estimates, exact, errors, 1, 0,
                0, readIngestTime, tableNs, tableNames,tableJoinKeys, numJoins, joins,
                selections, queryTimes, new int[0], new int[2][0], config.maxLocalWidth, config.seed);
    }

    private long readTime(JoinDataset jd, DuckDBConnection con) {
        long i = 0;
        long start = System.currentTimeMillis();
        for (String t : jd.tableNames) {
            System.out.println("Ingesting table " + t);
            int numColumns = jd.numColumnsPerTable.get(t);
            String dataQuery = "SELECT * FROM " + t;
            try (Statement stmt = con.createStatement();
                 ResultSet rs = stmt.executeQuery(dataQuery)
            ) {
                while (rs.next()) {
                    int[] rec = new int[numColumns];
                    for (int col = 1; col <= numColumns; col++) {
                        rec[col-1] = rs.getString(col).hashCode();
                    }
                    i+= rec[0];
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        long end = System.currentTimeMillis();
        return end-start;

    }

    private void setExactHashMaps(
            HashMap<String, HashMap<String, Boolean>> exactHashable,
            HashMap<String, HashMap<String, HashMap<String, Integer>>> exactHashMap) {

        // One shared value -> integer mapping per global join key.
        HashMap<String, HashMap<String, Integer>> globalJoinKeyMaps =
                new HashMap<>();

        /*
         * First construct shared mappings for exact-hashable join keys.
         */
        for (Map.Entry<String, Map<String, String>> joinEntry
                : d.globalJoinKeyToTableColumn.entrySet()) {

            String globalJoinKey = joinEntry.getKey();   // e.g. J0
            Map<String, String> tableColumns = joinEntry.getValue();

            /*
             * Determine whether this global join key should be exact-hashed.
             * We assume exactHashable stores J0/J1/... as the logical key.
             */
            boolean shouldExactHash = false;

            for (String table : tableColumns.keySet()) {
                HashMap<String, Boolean> tableExact = exactHashable.get(table);

                if (tableExact != null &&
                        Boolean.TRUE.equals(tableExact.get(globalJoinKey))) {
                    shouldExactHash = true;
                    break;
                }
            }

            if (!shouldExactHash) {
                continue;
            }

            HashMap<String, Integer> sharedMap = new HashMap<>();

            /*
             * Collect the union of distinct values from every physical
             * column belonging to this global join key.
             */
            for (Map.Entry<String, String> tableColumn : tableColumns.entrySet()) {

                String table = tableColumn.getKey();
                String column = tableColumn.getValue();

                String query =
                        "SELECT DISTINCT " + quoteIdentifier(column) +
                                " FROM " + quoteIdentifier(table);

                try (Statement stmt = con.createStatement();
                     ResultSet rs = stmt.executeQuery(query)) {

                    while (rs.next()) {
                        String value = rs.getString(1);

                        if (value != null && !sharedMap.containsKey(value)) {
                            sharedMap.put(value, sharedMap.size());
                        }
                    }

                } catch (SQLException e) {
                    throw new RuntimeException(
                            "Failed to construct exact hash map for join key "
                                    + globalJoinKey
                                    + " from " + table + "." + column,
                            e
                    );
                }
            }

            globalJoinKeyMaps.put(globalJoinKey, sharedMap);
        }

        /*
         * Now build the per-table maps.
         */
        for (String table : d.tableNames) {

            HashMap<String, Boolean> tableExact = exactHashable.get(table);

            if (tableExact == null || tableExact.isEmpty()) {
                continue;
            }

            HashMap<String, HashMap<String, Integer>> tableMap =
                    new HashMap<>();

            for (Map.Entry<String, Boolean> entry : tableExact.entrySet()) {

                if (!Boolean.TRUE.equals(entry.getValue())) {
                    continue;
                }

                String logicalColumn = entry.getKey();

                /*
                 * Case 1: this is a global join key such as J0.
                 */
                Map<String, String> joinKeyColumns =
                        d.globalJoinKeyToTableColumn.get(logicalColumn);

                if (joinKeyColumns != null && joinKeyColumns.containsKey(table)) {

                    String physicalColumn = joinKeyColumns.get(table);

                    HashMap<String, Integer> sharedMap =
                            globalJoinKeyMaps.get(logicalColumn);

                    if (sharedMap == null) {
                        throw new IllegalStateException(
                                "No shared exact mapping found for join key "
                                        + logicalColumn
                        );
                    }

                    /*
                     * Same map object is intentionally reused across tables.
                     */
                    tableMap.put(physicalColumn, sharedMap);

                    continue;
                }

                /*
                 * Case 2: ordinary non-join column.
                 * Its mapping only needs to be consistent within this table.
                 */
                String physicalColumn = logicalColumn;

                HashMap<String, Integer> valueMap = new HashMap<>();

                String query =
                        "SELECT DISTINCT " + quoteIdentifier(physicalColumn) +
                                " FROM " + quoteIdentifier(table);

                try (Statement stmt = con.createStatement();
                     ResultSet rs = stmt.executeQuery(query)) {

                    while (rs.next()) {
                        String value = rs.getString(1);

                        if (value != null && !valueMap.containsKey(value)) {
                            valueMap.put(value, valueMap.size());
                        }
                    }

                } catch (SQLException e) {
                    throw new RuntimeException(
                            "Failed to construct exact hash map for "
                                    + table + "." + physicalColumn,
                            e
                    );
                }

                tableMap.put(physicalColumn, valueMap);
            }

            exactHashMap.put(table, tableMap);
        }
    }
    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private HashMap<String, Integer> getParMap(long ram, HashMap<String, String[]> attrMap) {
        int num_tables = attrMap.size();

        if (num_tables == 0) {
            return null;
        }

        // One sampled tuple per table costs 4 bytes per column.
        int bytes_per_tuple =0;
        for (String[] attrs: attrMap.values()){
            bytes_per_tuple += 32 * attrs.length;
        }

        // Same number of tuples for every table.
        int samples_per_table = (int) ram / bytes_per_tuple;

        HashMap<String, Integer> parMap = new HashMap<>();

        for (String t : attrMap.keySet()) {
            parMap.put(t, samples_per_table);
        }
        return parMap;
    }

    private HashMap<String, int[]> getParMapASH(long ram, HashMap<String, Integer> tableSizes, HashMap<String, String[]> attrMap,
                                                  HashMap<String, List<String>> jksPerTable,
                                                HashMap<String, Buckets> globalJoinKeyBins,
                                                  double ingestBuffer,
                                                int uqratio) {
        HashMap<String, int[]> parMap = new HashMap<>();

        if (attrMap.isEmpty()) {
            return parMap;
        }

        HashMap<String, Long> tupleCost = new HashMap<>();

        for (String table : attrMap.keySet()) {

            int numJoinKeys = jksPerTable
                    .getOrDefault(table, Collections.emptyList())
                    .size();

            long cost =
                    32L * (attrMap.get(table).length + numJoinKeys)
                            + 5L * 64L
                            + 2L * 32L;

            tupleCost.put(table, cost);
        }

        HashSet<String> remaining =
                new HashSet<>(attrMap.keySet());

        HashMap<String, Integer> sampleSizes =
                new HashMap<>();

        HashSet<String> fullySampled =
                new HashSet<>();

        long remainingRam = ram;

        /*
         * Repeatedly give every remaining table an equal RAM share.
         * Tables whose complete contents fit in that share are removed
         * and their unused RAM becomes available to the other tables.
         */
        while (!remaining.isEmpty()) {

            long ramPerTable =
                    remainingRam / remaining.size();

            HashSet<String> completed =
                    new HashSet<>();

            for (String table : remaining) {

                long fullTableRam =
                        tupleCost.get(table)
                                * (long) tableSizes.get(table);

                if (fullTableRam <= ramPerTable) {

                    sampleSizes.put(
                            table,
                            tableSizes.get(table)
                    );

                    fullySampled.add(table);
                    completed.add(table);

                    /*
                     * Only the RAM actually used by the full table is
                     * consumed. The unused part of its share remains
                     * available.
                     */
                    remainingRam -= fullTableRam;
                }
            }

            if (completed.isEmpty()) {
                break;
            }

            remaining.removeAll(completed);
        }

        /*
         * Allocate the remaining RAM equally across all remaining
         * (partially sampled) tables.
         */
        if (!remaining.isEmpty()) {

            /*
             * Reserve one tuple for the reservoir/buffer of each
             * partially sampled table.
             */
//            long bufferRam = 0L;
            long totalTupleCost = 0L;
            for (String table : remaining) {
                totalTupleCost+= tupleCost.get(table);
//                bufferRam += 100*tupleCost.get(table);
            }

            long samplingRam =
                    Math.max(0L, remainingRam);

//            long ramPerTable =
//                    samplingRam / remaining.size();
            long samplesPerTable = samplingRam/totalTupleCost;
            for (String table : remaining) {

//                long cost = tupleCost.get(table);
//                int N = tableSizes.get(table);

//                int samples = (int) Math.min(
//                        N,
//                        ramPerTable / cost
//                );

                sampleSizes.put(table, (int) samplesPerTable);
            }
        }

        /*
         * Construct final map.
         */
        for (String table : attrMap.keySet()) {

            int samples =
                    sampleSizes.getOrDefault(table, 0);
            int N = tableSizes.get(table);
            double sampleRate = (double) samples / N;
            if (fullySampled.contains(table)) {
                parMap.put(table, new int[]{samples, 1});
                continue;
            }

            int bufferSize = (int) (sampleRate * uqratio);
            if (bufferSize >= samples) {
                bufferSize = (int) (samples * 0.01);
            }
//            int bufferSize = (int) (0.05*samples);
            /*
             * For a fully sampled table, bufferSize=1 but it represents
             * no additional reserved sampling memory.
             */
            if (bufferSize <= 1) { // completely sampled table.
                parMap.put(
                        table,
                        new int[]{samples - 1, 1}
                );
            } else {
                parMap.put(
                    table,
                    new int[]{samples - bufferSize, bufferSize}
            );}

        }

        return parMap;

//        HashMap<String, int[]> parMap = new HashMap<>();
//        if (attrMap.isEmpty()) {
//            return null;
//        }
//
//        HashMap<String, Long> tupleCost = new HashMap<>();
//
//        long costOneTuplePerTable = 0;
//
//        for (String table : attrMap.keySet()) {
//
//            int numJoinKeys = jksPerTable
//                    .getOrDefault(table, Collections.emptyList())
//                    .size();
//
//            long cost =
//                    32L * (attrMap.get(table).length + numJoinKeys)
//                            + 5L * 64
//                            + 2L * 32;
//
//            tupleCost.put(table, cost);
//            costOneTuplePerTable += cost;
//        }
//
//        int bufferSize = (int) Math.max(
//                1L,
//                8_000_000L / costOneTuplePerTable
//        );
//
//        long bufferRam = bufferSize * costOneTuplePerTable;
//
//        // Do not reserve more RAM than we actually have.
//        if (bufferRam >= ram) {
//            bufferSize = 1;
//            bufferRam = costOneTuplePerTable;
//        }
//
//        long remainingRam = Math.max(0L, ram - bufferRam);
//
//        HashSet<String> remainingTables = new HashSet<>(attrMap.keySet());
//
//        HashMap<String, Integer> sampleSizes = new HashMap<>();
//
//        while (!remainingTables.isEmpty() && remainingRam > 0) {
//
//            double totalWeight = 0.0;
//            double totalCost = 0.0;
//            for (String table : remainingTables) {
//                long N = tableSizes.get(table);
//                long cost = tupleCost.get(table);
//                totalCost += (double) cost;
//                totalWeight += (double) N * cost;
//            }
//
//            if (totalWeight == 0.0) {
//                break;
//            }
//
//            boolean tableCompleted = false;
//
//            /*
//             * First determine whether any table can be sampled completely
//             * under the current proportional allocation.
//             */
//            ArrayList<String> completedTables = new ArrayList<>();
//
//            for (String table : remainingTables) {
//
//                int N = tableSizes.get(table);
//                long cost = tupleCost.get(table);
//
//                double weight = (double) N * cost;
//                long allocatedRam =
//                        (long) (remainingRam * (cost / totalCost));
//
//                long fullTableRam = (long) N * cost;
//
//                if (allocatedRam >= fullTableRam) {
//
//                    sampleSizes.put(table, N);
//
//                    remainingRam -= fullTableRam;
//                    completedTables.add(table);
//
//                    tableCompleted = true;
//                }
//            }
//
//            /*
//             * If one or more tables were saturated, remove them and
//             * recompute the proportional allocation for the remainder.
//             */
//            if (tableCompleted) {
//                completedTables.forEach(remainingTables::remove);
//                continue;
//            }
//
//            /*
//             * No table can be fully sampled. The proportional allocation
//             * is therefore final.
//             */
//            for (String table : remainingTables) {
//
//                int N = tableSizes.get(table);
//                long cost = tupleCost.get(table);
//
//                double weight = (double) N * cost;
//                long allocatedRam =
//                        (long) (remainingRam * (weight / totalWeight));
//
//                int samples = (int) Math.min(
//                        N,
//                        allocatedRam / cost
//                );
//
//                sampleSizes.put(table, samples);
//            }
//
//            break;
//        }
//
//        /*
//         * Tables may receive zero samples if the RAM budget is extremely
//         * small.
//         */
//        for (String table : attrMap.keySet()) {
//
//            int samples = sampleSizes.getOrDefault(table, 0);
//
//            parMap.put(
//                    table,
//                    new int[]{samples, bufferSize}
//            );
//        }
//
//        return parMap;
//        // One sampled tuple per table costs 4 bytes per column.
//        int bytes_per_tuple =0;
//        for (String tableName: attrMap.keySet()){
//            int N = tableSizes.get(tableName);
//            int numJoinKeys = 0;
//            if (!jksPerTable.getOrDefault(tableName,new ArrayList<>()).isEmpty()){
//                numJoinKeys = jksPerTable.get(tableName).size();
//            }
//            bytes_per_tuple += 32 * (attrMap.get(tableName).length + numJoinKeys) + 5 * 64 + 2 *32;
//        }
//
//        // Same number of tuples for every table.
//        int samples_per_table = (int) ram / bytes_per_tuple;
////        int bufferSize = (int) Math.max((ingestBuffer * samples_per_table), 1);
//        int bufferSize = (int) Math.max(1, 8000000/bytes_per_tuple);
//        if (bufferSize>= samples_per_table) {
//            bufferSize = 1;
//        }
//        samples_per_table = samples_per_table - bufferSize;
//        HashMap<String, int[]> parMap = new HashMap<>();
//
//        for (String t : attrMap.keySet()) {
//            parMap.put(t, new int[]{samples_per_table, bufferSize});
//        }
//        return parMap;
    }

    private static int getNumColumns(HashMap<String, String[]> attrMap, int numColumns) {
        for (String table : attrMap.keySet()){
            String[] attrs = attrMap.get(table);
            int numAttributes = attrs.length;
            numColumns += numAttributes;
        }
        return numColumns;
    }

    private HashMap<String, HashMap<String, Integer>> readWidthMaps(String cardinalityMapPath,
                                                                    HashMap<String, Integer> joinKeyWidthMap,
                                                                    HashMap<String, HashMap<String,Boolean>> exactHashable,
                                                                    int maxLocalWidth) {
        HashMap<String, HashMap<String, Integer>> cardMap = readCardMaps(cardinalityMapPath);
        HashMap<String, HashMap<String, Integer>> widthMap = new HashMap<>();

        for (String table : cardMap.keySet()) {
            HashMap<String, Integer> cardMapTable = cardMap.get(table);
            for (String orgColName : cardMapTable.keySet()) {
                String colName = getColName(table, orgColName, d.globalColToJoinKey);
                if (joinKeyWidthMap.containsKey(colName)) {
                    widthMap.computeIfAbsent(table, k-> new HashMap<>()).put(orgColName, joinKeyWidthMap.get(colName));
                    System.out.println("Table " + table + " column " + orgColName + " width: " +joinKeyWidthMap.get(colName));
                    if (joinKeyWidthMap.get(colName) < Math.min(config.joinWidth, 30)) {
                        HashMap<String, Boolean> exH= exactHashable.computeIfAbsent(table, k->new HashMap<>());
                        exH.put(colName, true);
                    }
                } else {
                    int card = cardMap.get(table).get(colName);
                    if (card <= Math.min(maxLocalWidth, 30)) {
                        HashMap<String, Boolean> exH= exactHashable.computeIfAbsent(table, k->new HashMap<>());
                        exH.put(colName, true);
                        widthMap.computeIfAbsent(table, k-> new HashMap<>()).put(colName,card);
                        System.out.println("Table " + table + " column " + colName + " width: " +card);

                    } else {
                        widthMap.computeIfAbsent(table, k -> new HashMap<>()).put(colName,Math.min(maxLocalWidth, 5*card));
                        System.out.println("Table " + table + " column " + colName + " width: " +Math.min(maxLocalWidth, 5*card));
                    }


                }
            }
        }

        return widthMap;
    }

    public class Buckets {
        public int min;
        public int nbins;
        public double binSize;
        int max;
        int[] bins;
        public Buckets(int min, int max) {
            this.min = min;
            this.max = max;
        }

        public void update(int newMin, int newMax) {
            if (newMin < min) {
                min = newMin;
            }
            if (newMax > max) {
                max = newMax;
            }
        }

        public void makeBuckets(int nbins){
            bins = new int[nbins];
            this.nbins = nbins;
            double domain = max - min;
            if (domain < nbins) {
                binSize = 1.0;
                bins = new int[(int) domain];
                for (int i = 0; i < domain; i++) {
                    bins[i] = min + i+1;
                }
                this.nbins = (int) domain + 1;
            } else {
                binSize = domain /nbins;
                for (int i = 0; i < nbins; i++) {
                    bins[i] = (int) (min + binSize * (i + 1));
                }
            }
            if (bins[bins.length - 1] != max) {
                throw new RuntimeException("Something wrong");
            }
        }

    }
    private HashMap<String, Buckets> readJoinKeyBins(String path, int n_bins, Map<SynopsisQuery.ColumnKey, String> globalColToJoinKey) {
        HashMap<String, Buckets> binMap =new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(path))) {

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

                String globJK = globalColToJoinKey.getOrDefault(new SynopsisQuery.ColumnKey(table, column),"");
                if (globJK.isEmpty()) {
                    continue;
                }

                int min = Integer.parseInt(parts[2].trim());
                int max = Integer.parseInt(parts[3].trim());
                if (binMap.containsKey(globJK)) {
                    Buckets rec = binMap.get(globJK);
                    rec.update(min, max);
                } else {
                    binMap.put(globJK, new Buckets(min, max));
                }
            }

        } catch (IOException e) {
            throw new RuntimeException("Failed to read FactorJoin file: " + path, e);
        }
        HashMap<String, int[]> returnBinMap =new HashMap<>();
        for (String t : binMap.keySet()) {
            binMap.get(t).makeBuckets(n_bins);
//            returnBinMap.put(t, bins);
        }

        return binMap;
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

    private void queryFJ(FactorJoinASH fj, long ingestTime) throws Exception {
        // execute queries in d on reservoir samples
        int numQueries = d.getQueries().length;
        String[] queryName = new String[numQueries];
        double[] estimates= new double[numQueries];
        double[] errors = new double[numQueries];
        double[] exact = new double[numQueries];
        long[] queryTimes = new long[numQueries];
        int[] numJoins = new int[numQueries];
        double[][] tableNs = new double[numQueries][];
        int[][] tableJoinKeys = new int[numQueries][];
        String[][] tableNames = new String[numQueries][];
        List<SynopsisQuery.JoinPredicate>[] joins = new List[numQueries];
        List<EqualsTo>[] selections = new List[numQueries];
        for (int i = 0; i < numQueries; i++) {
            Query q = d.getQueries()[i];
            if (q.getName().equals("q07.sql")) {
                System.out.println("debug");
            }
            long start = System.currentTimeMillis();
            org.synopses.factorJoin.FactorJoinASH.QueryEst qEst = fj.query(q.syn_query);
            long end = System.currentTimeMillis();
            long queryTime = end - start;
            double estimate = qEst.estimate();
            Map<String, FilteredTableASH> map = qEst.map();
            tableNs[i] = new double[map.size()];

            tableNames[i] = new String[map.size()];
            tableJoinKeys[i] = new int[map.size()];
            numJoins[i] = map.size();
            int j=0;
            for (FilteredTableASH ft : map.values()) {
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
            queryTimes[i] = queryTime;
        }

        writeResults(fj.setting, queryName, estimates, exact, errors, 1, fj.getMemory(),
                fj.getDatasetMemory(), ingestTime, tableNs, tableNames,tableJoinKeys, numJoins,
                joins, selections, queryTimes,
                fj.globalWidths, fj.getSamplesPerTable(),config.maxLocalWidth,config.seed);

    }
    private void queryCM(CMJoin cmj, long ingestTime) throws Exception {
        // execute queries in d on reservoir samples
        int numQueries = d.getQueries().length;
        String[] queryName = new String[numQueries];
        double[] estimates= new double[numQueries];
        double[] errors = new double[numQueries];
        double[] exact = new double[numQueries];
        long[] queryTimes = new long[numQueries];
        int[] numJoins = new int[numQueries];
        double[][] tableNs = new double[numQueries][];
        int[][] tableJoinKeys = new int[numQueries][];
        String[][] tableNames = new String[numQueries][];
        List<SynopsisQuery.JoinPredicate>[] joins = new List[numQueries];
        List<EqualsTo>[] selections = new List[numQueries];
        for (int i = 0; i < numQueries; i++) {
            Query q = d.getQueries()[i];
            long start = System.currentTimeMillis();
            if (q.getName().equals("query_134_216.sql")) {
                System.out.println("debug");
            }
            CMJoin.QueryEst qEst = cmj.query(q.syn_query);
            long end = System.currentTimeMillis();
            long queryTime = end - start;
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
            queryTimes[i] = queryTime;
        }

        writeResults(cmj.setting, queryName, estimates, exact, errors, cmj.getDepth(), cmj.getMemory(),
                cmj.getDatasetMemory(), ingestTime, tableNs, tableNames,tableJoinKeys, numJoins, joins,
                selections, queryTimes, cmj.globalWidths, new int[2][0], config.maxLocalWidth, config.seed);
    }

    private void writeResults(String setting,String[] queryName, double[] estimates, double[] exact,
                              double[] errors, int d, long ram, long datasetMemory,
                              long ingestTime, double[][] tableNs, String[][] tableNames, int[][] tableJoinKeys, int[] numTables,
                              List[] joins, List[] selections, long[] queryTimes,
                              int[] globalJoinKeyWidths, int[][] samplesPerTable, int maxLocalWidth, int repetition) throws Exception {


        boolean init = !new File(CSV_FILE_NAME).exists();

        CSVWriter writer = new CSVWriter(new FileWriter(CSV_FILE_NAME, true));
        if (init) {
            String[] header = new String[]{"queryID","setting","memUsage","estimate","exact","error","absError","relError",
                    "d","datasetMemory","ingestTime","tableNs","tableNames","tableJoinKeys","numTables","joins","selections","queryTime",
            "globalJoinKeyWidths","samplesPerTable","bufferPerTable","maxLocalWidth","repetition"};
            writer.writeNext(header);
        }
        for  (int i = 0; i < estimates.length; i++) {
            String[] result = new String[23];
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
            result[17] = String.valueOf(queryTimes[i]);
            result[18] = Arrays.toString(globalJoinKeyWidths);
            result[19] = Arrays.toString(samplesPerTable[0]);
            result[20] = Arrays.toString(samplesPerTable[1]);
            result[21] = String.valueOf(maxLocalWidth);
            result[22] = String.valueOf(repetition);

            writer.writeNext(result);
        }
        writer.close();

    }


}