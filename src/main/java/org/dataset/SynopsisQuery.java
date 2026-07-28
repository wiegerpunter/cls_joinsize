package org.dataset;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

import java.util.*;

public class SynopsisQuery {
    public List<Table> tables;
    public List<EqualsTo> selections = new ArrayList<>();
    public List<JoinPredicate> joins = new ArrayList<>();
    @Override
    public String toString() {
        return "Tables=" + tables +
                "\nSelections=" + selections +
                "\nJoins=" + joins;
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

    public record SelectionPredicate(
            String table,
            String column,
            String value
    ) {}

    public record JoinPredicate(
            Column left, Column right
    ) {}
}
