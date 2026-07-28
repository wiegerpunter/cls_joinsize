package org.dataset;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
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
        parseTables(select);
        parseWhere(select);
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

    }

    private void parseTables(PlainSelect select) {
        this.syn_query.tables = new ArrayList<>();

        this.syn_query.tables.add((Table) select.getFromItem());

        if (select.getJoins() != null) {
            for (Join j : select.getJoins()) {
                if (j.isSimple()) {
                    this.syn_query.tables.add((Table) j.getRightItem());
                }
            }
        }
    }
    public static List<Expression> flattenWhere(Expression where) {
        List<Expression> predicates = new ArrayList<>();
        flatten(where, predicates);
        return predicates;
    }

    private static void flatten(Expression expr, List<Expression> out) {
        if (expr instanceof AndExpression and) {
            flatten(and.getLeftExpression(), out);
            flatten(and.getRightExpression(), out);
        } else {
            out.add(expr);
        }
    }
    private void parseWhere(PlainSelect select) {
//        Expression where = select.getWhere();
        // flatten where tree and add either to selection predicates, or equality joins
        for (Expression expr : flattenWhere(select.getWhere())) {

            if (expr instanceof EqualsTo eq) {

                Expression left = eq.getLeftExpression();
                Expression right = eq.getRightExpression();

                if (left instanceof Column l && right instanceof Column r) {
                    // Equality join
                    this.syn_query.joins.add(new SynopsisQuery.JoinPredicate(l, r));
                } else {
                    // Selection predicate
                    this.syn_query.selections.add(eq);
                }

            }
            // Skip: IN, LIKE, BETWEEN, >, <, etc.
        }
    }
    public int getResult() {
        return query_result;
    }

    public String getName() {
        return query_name;
    }
}
