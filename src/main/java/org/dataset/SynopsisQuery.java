package org.dataset;

import java.util.*;

public class SynopsisQuery {
    public Set<String> tables = new HashSet<>();
    public List<SelectionPredicate> selections = new ArrayList<>();
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
            String leftTable,
            String leftColumn,
            String rightTable,
            String rightColumn
    ) {}
}
