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
import java.util.concurrent.atomic.AtomicInteger;
record ColumnKey(String table, String column) {
    static ColumnKey of(Column c) {
        return new ColumnKey(
                c.getTable().getName(),
                c.getColumnName()
        );
    }
}
public class SynopsisQuery {
    public List<Table> tables;
    public List<EqualsTo> selections = new ArrayList<>();
    public List<JoinPredicate> joins = new ArrayList<>();
    public HashMap<String, String> aliasMap;
    private final Map<ColumnKey, String> columnToJoinKey = new HashMap<>();
    private final Map<String, Map<String, Column>> joinKeyToTableColumn = new HashMap<>();
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
        buildEquivalentJoinKeys();
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
    ) {}


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
                }

            }
            // Skip: IN, LIKE, BETWEEN, >, <, etc.
        }
    }

    private void buildEquivalentJoinKeys() {
        boolean[] addedJoin = new boolean[joins.size()];
        int counter =1;
        for (int i = 0; i<joins.size(); i++){
            if (addedJoin[i]) {
                continue;
            }
            String tleft = joins.get(i).left().getTableName();
            Column cleft = joins.get(i).left();
            String tright = joins.get(i).right().getTableName();
            Column cright = joins.get(i).right();
            if (!columnToJoinKey.containsKey(ColumnKey.of(cleft)) && !columnToJoinKey.containsKey(ColumnKey.of(cright))) {
                String key = "J" + counter;
                counter += 1;
                columnToJoinKey.put(ColumnKey.of(cleft), key);
                columnToJoinKey.put(ColumnKey.of(cright), key);
                HashMap<String, Column> mapOfKey = new HashMap<>();
                mapOfKey.put(tleft, cleft);
                mapOfKey.put(tright, cright);
                joinKeyToTableColumn.put(key, mapOfKey);
            } else if (columnToJoinKey.containsKey(ColumnKey.of(cleft)) && !columnToJoinKey.containsKey(ColumnKey.of(cright))) {
                String key = columnToJoinKey.get(ColumnKey.of(cleft));
                columnToJoinKey.put(ColumnKey.of(cright), key);
                HashMap<String, Column> mapOfKey = (HashMap<String, Column>) joinKeyToTableColumn.get(key);
                mapOfKey.put(tright, cright); // check if map needs to be put back or if sufficient.
            } else if (!columnToJoinKey.containsKey(ColumnKey.of(cleft)) && columnToJoinKey.containsKey(ColumnKey.of(cright))) {
                String key = columnToJoinKey.get(ColumnKey.of(cright));
                columnToJoinKey.put(ColumnKey.of(cleft), key);
                HashMap<String, Column> mapOfKey = (HashMap<String, Column>) joinKeyToTableColumn.get(key);
                mapOfKey.put(tleft, cleft); // check if map needs to be put back or if sufficient.
            }
            addedJoin[i] = true;
        }
    }
}
