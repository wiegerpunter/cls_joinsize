package org.dataset;

import net.sf.jsqlparser.JSQLParserException;
import org.duckdb.DuckDBConnection;
import org.utils.Config;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class DSU {
    Map<String, String> parent = new HashMap<>();

    String find(String x) {
        parent.putIfAbsent(x, x);
        if (!parent.get(x).equals(x)) {
            parent.put(x, find(parent.get(x)));
        }
        return parent.get(x);
    }

    void union(String a, String b) {
        String pa = find(a);
        String pb = find(b);
        if (!pa.equals(pb)) {
            parent.put(pa, pb);
        }
    }

    Map<String, Set<String>> groups() {
        Map<String, Set<String>> res = new HashMap<>();
        for (String x : parent.keySet()) {
            String root = find(x);
            res.computeIfAbsent(root, k -> new HashSet<>()).add(x);
        }
        return res;
    }
}
public class JoinDataset {
    DuckDBConnection con;
    // Class contains the equivalent join keys, names of all tables,
    private final Query[] queries;
    public String[] tableNames;
    public HashMap<String, Integer> numColumnsPerTable;
    public HashMap<String, String[]> attrMap;

    public JoinDataset(DuckDBConnection con, Config config) throws SQLException, IOException, JSQLParserException {
        this.con = con;
        this.queries = this.readQueries(config);
        getExactAnswers();
        assert queries != null;
        for (Query q: queries) {
            System.out.println("Query " + q.query_name + " has answer: " + q.query_result);
        }
        this.getTableNames();
        Map<String, Set<String>> eqjkg = this.getEquivalentJoinKeyGroups();
     }

    private void getExactAnswers() throws SQLException {
        // execute query on duckdb
        for (Query q: queries) {
            try (Statement stmt = con.createStatement();
                 ResultSet rs = stmt.executeQuery(q.query_text)) {

                while (rs.next()) {
                    q.query_result = rs.getInt(1);
                }
            }
        }
    }

    private Query[] readQueries(Config config) throws IOException, JSQLParserException {
        // read all .sql queries in config.queryPath;
        File dir = new File(config.queryPath);

        File[] files = dir.listFiles((d, name)->name.endsWith(".sql"));
        assert files != null;
        ArrayList<Query> queries = new ArrayList<>();
        for (File f : files){
            BufferedReader br = new BufferedReader(new FileReader(f.getAbsoluteFile()));
            if (f.getName().contains("fkindexes") || f.getName().contains("schema") || f.getName().contains("15")) {
                continue;
            } else {
                Query q = new Query(f.getName(), br.readLine());
                if (q.syn_query!=null) {
                    queries.add(q);
                }
            }
        }
        if (!queries.isEmpty()) {
            Query[] qs = new Query[queries.size()];
            for (int i = 0; i<qs.length; i++){
                qs[i] = queries.get(i);
            }
            return qs;
        }
        return null;
    }
    static String normalize(String aliasColumn,
                            Map<String, String> aliasToTable) {

        String[] parts = aliasColumn.split("\\.");

        String alias = parts[0];
        String col = parts[1];

        String table = aliasToTable.get(alias);
        if (table == null) {
            throw new RuntimeException(
                    "Unknown alias: " + alias
            );
        }
        return table + "." + col;
    }

    static Map<String, String> buildAliasMap(String sql) {

        Map<String, String> map = new HashMap<>();

        Pattern p = Pattern.compile("(\\w+)\\s+(?:as\\s+)?(\\w+)", Pattern.CASE_INSENSITIVE);

        Matcher m = p.matcher(sql.split("where")[0]);

        while (m.find()) {
            String table = m.group(1);
            String alias = m.group(2);
            map.put(alias, table);
        }

        return map;
    }

    private Map<String, Set<String>> getEquivalentJoinKeyGroups() {
        Pattern fromPattern =
                Pattern.compile("from\\s+(.*?)\\s+where", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

        Pattern joinPattern = Pattern.compile("(\\w+\\.\\w+)\\s*=\\s*(\\w+\\.\\w+)");

        DSU dsu = new DSU();

        for (Query q : queries) {
            Matcher mf = fromPattern.matcher(q.query_text);

            if (!mf.find()) {
                continue; // or throw error
            }

            String fromBlock = mf.group(1);
            Map<String, String> aliasToTable = buildAliasMap(fromBlock);
            Matcher m = joinPattern.matcher(q.query_text);
            while (m.find()) {
                String left = normalize(m.group(1), aliasToTable);
                String right = normalize(m.group(2), aliasToTable);

                dsu.union(left, right);
            }
        }

        return dsu.groups();
    }

    private void getTableNames() throws SQLException {
        var rs = con.createStatement()
                .executeQuery("SHOW TABLES");

        ArrayList<String> tableNamesList = new ArrayList<>();
        while (rs.next()) {
            tableNamesList.add(rs.getString(1));
        }
        tableNames = new String[tableNamesList.size()];
        numColumnsPerTable = new HashMap<>();
        attrMap = new HashMap<>();
        String columnQuery = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE table_name=?";
        String columnNamesQuery = "SELECT * FROM INFORMATION_SCHEMA.COLUMNS WHERE table_name=?";
        for (int i = 0; i < tableNamesList.size(); i++) {
            tableNames[i] = tableNamesList.get(i);
            //number of columns
            try (PreparedStatement stmt = con.prepareStatement(columnQuery)) {
                stmt.setString(1, tableNames[i]);
                try (ResultSet res = stmt.executeQuery()) {
                    if (res.next()) {
                        numColumnsPerTable.put(tableNames[i], res.getInt(1));
                    }
                }
            }
            try (PreparedStatement stmt = con.prepareStatement(columnNamesQuery)) {
                stmt.setString(1, tableNames[i]);
                try (ResultSet res = stmt.executeQuery()) {
                    ArrayList<String> attrs =
                            new ArrayList<>();

                    while (res.next()) {
                        attrs.add(
                                res.getString("column_name")
                        );
                    }

                    attrMap.put(
                            tableNames[i],
                            attrs.toArray(new String[0])
                    );
                }
            }
        }
    }

    public Query[] getQueries() {
        return queries;
    }


}
