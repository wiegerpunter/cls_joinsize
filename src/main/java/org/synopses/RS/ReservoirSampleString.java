package org.synopses.RS;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import org.dataset.SynopsisQuery;
import org.synopses.AbstractSynopsis;

import java.util.*;

public class ReservoirSampleString extends AbstractSynopsis {
    final String[][] reservoir;
    private final int maxSampleSize;
    private int size; // sample size
    private int count; // population size
    private final String table;
    private final String[] columns;
    Random rn;

    public ReservoirSampleString(String table, String[] columns, int ram, int numAttributes, int seed) {
        setting = "Reservoir Sample";
        this.table = table;
        this.columns = columns;
        this.ram = ram;
        this.maxSampleSize = ram / (4 * numAttributes);
        this.count = 0;
        rn = new Random(seed);
        reservoir = new String[maxSampleSize][numAttributes];
    }

    public ReservoirSampleString(ReservoirSampleString rs) {
        this.setting = rs.setting;
        this.table = rs.table;
        this.columns = rs.columns;
        this.ram = rs.ram;
        this.size = rs.size;
        this.maxSampleSize = rs.maxSampleSize;
        this.count = rs.count;
        this.rn = rs.rn;
        this.reservoir = rs.reservoir.clone();
    }

    public ReservoirSampleString(ReservoirSampleString rs, ArrayList<String[]> filtered) {
        this.setting = rs.setting;
        this.table = rs.table;
        this.columns = rs.columns;
        this.ram = rs.ram;
        this.rn = rs.rn;
        this.reservoir = new String[filtered.size()][];
        for (int i= 0; i < filtered.size(); i++) {
            reservoir[i] = filtered.get(i);
        }
        this.size = filtered.size();
        this.maxSampleSize = rs.maxSampleSize;
        this.count = (int) Math.round(rs.count * ((double) size / rs.size));
    }

    public void add(String[] rec) {
        if (count < maxSampleSize) {
            reservoir[count] = rec.clone();
            size++;
        } else {
            int replace = rn.nextInt(count + 1);
            if (replace < maxSampleSize) {
                reservoir[replace] = rec.clone();
            }
        }
        count++;
    }

    public int query(String[] query) {
        int queryResultCount = 0;
        for (int i = 0; i < size; i++) {
            boolean match = true;
            for (int j = 0; j < query.length; j++) {
                if (!Objects.equals(query[j], "-1") && !Objects.equals(query[j], reservoir[i][j])) {
                    match = false;
                    break;
                }
            }
            if (match) {
                queryResultCount++;
            }
        }
        // scale result by the size of the reservoir
        return (int) (queryResultCount * (Math.max((double) count / size, 1)));
    }

    @Override
    public void addIntArr(int[] record) {

    }

    @Override
    public int query(int[] record) {
        return 0;
    }

    public int memoryUsage() {
        int ram;
        ram = size * reservoir[0].length * 4;
        return ram;
    }

    public ReservoirSampleString filtered(List<EqualsTo> spList) {
        if (spList.isEmpty()) {
            return new ReservoirSampleString(this);
        }
        ArrayList<String[]> filteredReservoir = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (reservoir[i][0] == null) {
                continue;
            }
            boolean add = true;
            for (EqualsTo sp : spList) {
                int col = getIdCol(((Column) sp.getLeftExpression()).getColumnName());
                if (reservoir[i][col]==null) { // check if null
                    add=false;
                    break;
                }
                if (!reservoir[i][col].equals(((StringValue) sp.getRightExpression()).getValue())) {
                    add = false;
                    break;
                }
            }
            if (add) {
                filteredReservoir.add(reservoir[i].clone());
            }
        }
        return new ReservoirSampleString(this, filteredReservoir);
    }

    public int getIdCol(String col) {
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].equals(col)) {
                return i;
            }
        }
        throw new RuntimeException("Column " + col + " not found for table" + table);
    }

    @Override
    public int getd() {
        return 0;
    }

    @Override
    public int getw() {
        return 0;
    }

    @Override
    public int getNumSamples() {
        return size;
    }

    public int size() {
        return size;
    }

    public int getCount() {
        return count;
    }

    @Override
    public int numLatent(int[] query) {
        return 0;
    }
}
