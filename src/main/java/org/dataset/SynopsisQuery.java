package org.dataset;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.select.Join;
import net.sf.jsqlparser.statement.select.PlainSelect;

import java.util.*;

public class SynopsisQuery {
    public List<Table> tables;
    public List<EqualsTo> selections = new ArrayList<>();
    public List<JoinPredicate> joins = new ArrayList<>();
    public Set<String> globalJoinKeysInQuerySet;
    public HashMap<String, String> aliasMap;
    public int numJoinKeys;
    public HashMap<String, Integer> jkToBucket;
    public HashMap<Integer, Integer> bucketToJK;

    public void setGlobalJoinKeysInQuery(Map<ColumnKey, String> globalColToJoinKey) {
        globalJoinKeysInQuerySet = new HashSet<>();
        for (JoinPredicate jp : joins) {

            ColumnKey ck = ColumnKey.of(jp.left(), aliasMap);

            String global = globalColToJoinKey.get(ck);

            globalJoinKeysInQuerySet.add(global);
        }
        numJoinKeys = globalJoinKeysInQuerySet.size();
        jkToBucket = new HashMap<>();
        bucketToJK = new HashMap<>();
        int i =0;
        for (String jk : globalJoinKeysInQuerySet) {
            jkToBucket.put(jk, i);
            bucketToJK.put(i, Integer.parseInt(jk.substring(1)));
            i++;
        }
    }

    public record ColumnKey(String table, String column) {
        public static ColumnKey of(Column c, HashMap<String, String> aliasMap) {
            return new ColumnKey(
                    aliasMap.get(c.getTable().getName()),
                    c.getColumnName()
            );
        }
    }

    @Override
    public String toString() {
        return "Tables=" + tables +
                "\nSelections=" + selections +
                "\nJoins=" + joins;
    }
    public SynopsisQuery(String query_text) throws JSQLParserException {
        PlainSelect select = (PlainSelect) CCJSqlParserUtil.parse(query_text);
        parseTables(select);
        setAliasMap();
        parseWhere(select);
    }

    public void setAliasMap(){
        aliasMap = new HashMap<>();
        // map alias --> name
        for (Table t : tables) {
            aliasMap.put(t.getAlias().getName(), t.getName());
        }
    }

    public List<EqualsTo> getSelectionPredicatesPerTable(Table table) {
        List<EqualsTo> selPerTable = new ArrayList<>();
        for (EqualsTo sp : selections) {
            if (table.getAlias().getName().equals(((Column) sp.getLeftExpression()).getTable().getName())) {
                selPerTable.add(sp);
            }
        }
        return selPerTable;
    }
    public record JoinPredicate(
            Column left, Column right
    ) {
        @Override
        public String toString() {
            return left.toString() +", " + right.toString();
        }

    }

    private void parseTables(PlainSelect select) {
        tables = new ArrayList<>();

        tables.add((Table) select.getFromItem());

        if (select.getJoins() != null) {
            for (Join j : select.getJoins()) {
                if (j.isSimple()) {
                    tables.add((Table) j.getRightItem());
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
                    joins.add(new SynopsisQuery.JoinPredicate(l, r));
                } else {
                    // Selection predicate
                    selections.add(eq);
//                    continue; // debug w/o local predicates
                }
            }
            // Skip: IN, LIKE, BETWEEN, >, <, etc.
        }
    }


}
