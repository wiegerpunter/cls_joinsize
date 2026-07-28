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

import java.util.*;
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
        // IMDB-JOB sql dialect

        // convert sql query to query that can be executed on a synopsis
        // take join queries into account here.
        this.syn_query = new SynopsisQuery();
        PlainSelect select = (PlainSelect) CCJSqlParserUtil.parse(query_text);

        SelectItem selectItem = select.getSelectItems().get(0);

        this.syn_query.tables = new ArrayList<>();

        this.syn_query.tables.add((Table) select.getFromItem());

        if (select.getJoins() != null) {
            for (Join j : select.getJoins()) {
                if (j.isSimple()) {
                    this.syn_query.tables.add((Table) j.getRightItem());
                }
            }
        }
        Expression where = select.getWhere();
        // parse equality predicates
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
