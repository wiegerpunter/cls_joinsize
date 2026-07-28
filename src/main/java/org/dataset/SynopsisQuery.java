package org.dataset;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;

import java.util.*;

public class SynopsisQuery {
    public List<Table> tables;
    public List<Expression> selections = new ArrayList<>();
    public List<JoinPredicate> joins = new ArrayList<>();
    @Override
    public String toString() {
        return "Tables=" + tables +
                "\nSelections=" + selections +
                "\nJoins=" + joins;
    }

    public List<SelectionPredicate> getSelectionPredicatesPerTable(String table) {
        List<SelectionPredicate> selPerTable = new ArrayList<>();
        for (SelectionPredicate sp : selections) {
            if (Objects.equals(sp.table, table)) {
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
