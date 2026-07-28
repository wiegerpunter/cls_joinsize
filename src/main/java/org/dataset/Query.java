package org.dataset;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.*;
import net.sf.jsqlparser.statement.select.*;
import net.sf.jsqlparser.util.TablesNamesFinder;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.dataset.JoinDataset.buildAliasMap;
import static org.dataset.JoinDataset.normalize;

public class Query {
    String query_name;
    final String query_text;
    int query_result;
    public SynopsisQuery syn_query;
    Query(String query_name, String query_text) throws JSQLParserException {
        this.query_name = query_name;
        this.query_text = query_text;
        buildSynQuery();
    }

    public void buildSynQuery() throws JSQLParserException {
        // convert sql query to query that can be executed on a synopsis
        // take join queries into account here.
        SynopsisQuery sq = new SynopsisQuery();
        PlainSelect select = (PlainSelect) CCJSqlParserUtil.parse(query_text);

        SelectItem selectItem = select.getSelectItems().get(0);

        Map<String, String> aliasMap = new HashMap<>();
        FromItem from = select.getFromItem();
        Set<String> TableNames = TablesNamesFinder.findTables(query_text);
        for (String tableName : TableNames){
            String alias = tableName.getAlias() == null
                    ? tableName
                    : tableName.getAlias().getName();

            aliasMap.put(alias, tableName);
        }
//        EqualsTo equalsTo = (EqualsTo) select.getWhere();

        if (select.getJoins() != null) {
            for (Join join : select.getJoins()) {
                Table table = (Table) join.getRightItem();

                String tableName = table.getName();

                String alias = table.getAlias() == null
                        ? tableName
                        : table.getAlias().getName();

                aliasMap.put(alias, tableName);
            }
        }
//        Expression where = ps.getWhere();
//        // parse equality predicates
//        Pattern predPattern = Pattern.compile(
//                "(\\w+\\.\\w+)\\s*=\\s*(\\w+\\.\\w+|'[^']*'|[^\\s;]+)",
//                Pattern.CASE_INSENSITIVE
//        );
//        Matcher mp = predPattern.matcher(query_text);
//
//        while (mp.find()) {
//
//            String left = normalize(mp.group(1), aliasMap);
//
//            String right =
//                    mp.group(2)
//                            .replace(";", "")
//                            .trim();
//
//            String[] leftParts = left.split("\\.");
//
//            //------------------------------------------------
//            // JOIN
////            //-----------------------------
//        }

        this.syn_query = sq;
    }

    public int getResult() {
        return query_result;
    }

    public String getName() {
        return query_name;
    }
}
