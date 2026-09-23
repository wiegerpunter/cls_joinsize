package org.synopses.factorJoin;

import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import org.dataset.SynopsisQuery;
import org.experiments.RunJoinExperiment;

import java.util.*;

public class ReservoirSample {
    private int[][] reservoir;
    private int count;
    private final int size;
    private final int numAttributes;
    Random rn;
    private final String table;
    private final String[] columns;
    public final boolean[] isJoinKey;
    public final HashMap<Integer, RunJoinExperiment.Buckets> joinKeyBinMap = new HashMap<>();

    public ReservoirSample(String t, String[] columns, Map<SynopsisQuery.ColumnKey, String> globalColumnToJoinKey,
                           int numAttributes, int size, int repetition,
                           HashMap<String, RunJoinExperiment.Buckets> globalJoinKeyBins) {
        this.table = t;
        this.columns = columns;
        this.size = size;
        this.numAttributes = numAttributes;
        this.reservoir = new int[size][numAttributes];
        this.count = 0;
        this.rn = new Random(repetition);
        isJoinKey = new boolean[numAttributes];
        for (int i = 0; i < numAttributes; i++) {
            String globJK = globalColumnToJoinKey.getOrDefault(new SynopsisQuery.ColumnKey(t,columns[i]),"");
            if (!globJK.isEmpty()) {
                isJoinKey[i] = true;
                RunJoinExperiment.Buckets joinKeyBins = globalJoinKeyBins.get(globJK);
                joinKeyBinMap.put(i, joinKeyBins);
            }
            // else no join key.
        }
        //this.memUsageSynopsis = (long) size * numAttributes * 32;
    }

    public void addArr(int[] record) {
        if (count < size) {
//            attr = new long[record.length - 1];
//            System.arraycopy(record, 1, attr, 0, record.length - 1); // ignore RID
            for (int i = 0; i < record.length; i++) {
                if (isJoinKey[i]) {
                    record[i] = getBin(record[i], joinKeyBinMap.get(i));
                }
            }
            reservoir[count] = record;
//            System.arraycopy(record, 0, reservoir[count], 0, record.length - 1);

        } else {
            int replace = rn.nextInt(count + 1);
            if (replace < size) {
                for (int i = 0; i < record.length; i++) {
                    if (isJoinKey[i]) {
                        record[i] = getBin(record[i], joinKeyBinMap.get(i));
                    }
                }
//                System.arraycopy(record, 0, reservoir[replace], 0, record.length - 1);  // Reuse existing row
                reservoir[replace] = record;
            }
        }
        count++;
    }
//
//    private int getBin(int value, int[] binMap) {
//        // bin 0: -X --- binMap[0]]
//        // bin 1: (binMap[0], binMap[1])
//        // ...
//        // bin binMap.length-1: (binMap[-2], binMap[-1]].
//        // no value higher than binMap[-1].
//        if (value <= binMap[0]) {
//            return 0;
//        }
//        for (int bin = 1; bin < binMap.length; bin++) {
//            if (value <= binMap[bin]) {
//                return bin;
//            }
//        }
//        throw new RuntimeException("Value larger than largest bin bound: val: " + value + ", bound: " + binMap[binMap.length-1]);
//    }

    private int getBin(int value, RunJoinExperiment.Buckets b) {
        int bin = (int) ((value - b.min) / b.binSize);

        if (bin < 0) {
            return 0;
        }
        if (bin >= b.nbins) {
            return b.nbins - 1;
        }

        return bin;
    }

    public void delete(int[] r) {
        int[] attr;
        if (count < size) {
//            attr = new int[r.length];
//            System.arraycopy(r, 1, attr, 0, r.length - 1); // ignore RID
            attr = r;
            for (int i = 0; i < count; i++) {
                // go over all attributes and see if match
                if (delete(attr, i)) break;
            }
        } else {
            int replace = rn.nextInt(count + 1);
            if (replace < size) {
//                attr = new int[r.length - 1];
//                System.arraycopy(r, 1, attr, 0, r.length - 1); // ignore RID
                attr = r;
                for (int i = 0; i < reservoir.length; i++) {
                    // go over all attributes and see if match
                    if (delete(attr, i)) break;
                }
            }
        }
        count--;
    }


    private boolean delete(int[] attr, int i) {
        boolean match = true;
        for (int j = 0; j < reservoir[i].length; j++) {
            if (reservoir[i][j] != attr[j]) {
                match = false;
                break;
            }
        }
        if (match) {
            Arrays.fill(reservoir[i], Integer.MIN_VALUE);  // Or any "tombstone" value you recognize
            return true;
        }

        return false;
    }

    public int query(int[] query, int numPreds) {
       // go over all records in the reservoir and check if they match the query
        int queryResultCount = 0;
        for (int i = 0; i < size; i++) {
            boolean match = true;
            for (int j = 0; j < query.length; j++) {
                if (query[j] != -1 && query[j] != reservoir[i][j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                queryResultCount++;
            }
        }
        // scale result by the size of the reservoir
        return (int) (queryResultCount * (Math.max((double) count /size, 1)));

    }

    public void printParams() {
        System.out.println("Reservoir Sample with size: " + size);

    }
    public int getMemoryUsage() {
        return (size * reservoir[0].length * 255);
    }

    public int getN() {
        return count;
    }

    public int getNumColumns() {
        return numAttributes;
    }

    public int getTotalJoinAssignments(List<SynopsisQuery.ColumnKey> unqColumns) {
        long total = 1L;

        for (SynopsisQuery.ColumnKey column : unqColumns) {
            int attr = getIdCol(column.column());
            total *= joinKeyBinMap.get(attr).nbins;

            if (total > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(
                        "Number of join-key bucket assignments exceeds integer range"
                );
            }
        }

        return (int) total;

        //throw new RuntimeException("not implemented yet.");

    }

    public int getIdCol(String col) {
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].equals(col)) {
                return i;
            }
        }
        throw new RuntimeException("Column " + col + " not found for table: " + table);

    }

    public int getPKw() {
        return joinKeyBinMap.get(0).nbins;

    }

    public double queryBucketMarginal(int pkColumn, int bucketValue) {
        int queryResultCount = 0;
        if (!isJoinKey[pkColumn]) {
            throw new RuntimeException("Wrong use of marginal query, not on join key.");
        }
        for (int i = 0; i < Math.min(count, size); i++) {
            if (bucketValue == -1) {
                continue;
            }
            if (bucketValue == reservoir[i][pkColumn]) {
                queryResultCount++;
            }
        }
        // scale result by the size of the reservoir
        double scale = count < size ? 1.0 : (double) count / size;

        return queryResultCount * scale;

    }

    public double[] queryBucketAssignment(int[] query, HashMap<Integer, Integer> bucketPerAttribute,
                                          HashSet<Integer> active, boolean pkHasPred) {
        // filtered and unfiltered
        // go over all records in the reservoir and check if they match the query
        int queryResultCount = 0;
        int unfilteredQueryResult = 0;

        for (int i = 0; i < Math.min(count, size); i++) {
            boolean match = true;
            boolean matchWOPredicates = true;
            for (int j = 0; j < query.length; j++) {
                if (query[j] == -1) {

                    // But if this is an active join key, we still
                    // need to restrict it to the requested bucket.
                    if (isJoinKey[j] && active.contains(j)) {
                        int bucket = bucketPerAttribute.get(j);

                        if (reservoir[i][j] != bucket) {
                            match= false;
                            matchWOPredicates = false;
                            break;
                        }
                    }
                    continue;
                }
                // Explicit predicate.
                if (reservoir[i][j] != query[j]) {
                    match = false;
                }
//                if (isJoinKey[j] && query[j] ==-1 && active.contains(j) &&
//                        bucketPerAttribute.get(j) != reservoir[i][j]) {
//                    matchWOPredicates = false;
//                    match = false;
//                    break;
//                } else if (!isJoinKey[j] && query[j] != reservoir[i][j]) {
//                    match = false;
//                }
            }
            if (matchWOPredicates) {
                unfilteredQueryResult++;
            }
            if (match) {
                queryResultCount++;
            }
        }
        // scale result by the size of the reservoir
        double scale = count < size ? 1.0 : (double) count / size;

        if (pkHasPred) {
            return new double[] {
                    Math.max(1.0, unfilteredQueryResult * scale),
                    queryResultCount * scale
            };
        }
        return new double[] {
                queryResultCount * scale
        };
    }


    public record queryInfo(int[] record, HashSet<Integer> active) {

    }

    public queryInfo queryInfo(List<EqualsTo> selectionPredicatesPerTable, List<SynopsisQuery.ColumnKey> unqColumns) {
        int[] record = new int[numAttributes];
        Arrays.fill(record, -1);
        HashSet<Integer> active = new HashSet<>();

        // local predicates
        for (EqualsTo eq : selectionPredicatesPerTable) {

            String col = ((Column) eq.getLeftExpression()).getColumnName();
            String value = literalToString(eq.getRightExpression());

            int attr = getIdCol(col);
            if (attr < 0)
                continue;
            if (isJoinKey[attr]) {
                // hash to column
                record[attr] = getBin(Integer.parseInt(value), joinKeyBinMap.get(attr));
            } else {
                record[attr] = value.hashCode(); // same as ingestion
            }
            active.add(attr);
        }
        int joinAttrRoot = -1;

        // determine join attribute
        for (SynopsisQuery.ColumnKey unqColumn : unqColumns) {
            int attr = getIdCol(unqColumn.column());
            if (!active.contains(attr)) {
                if (joinAttrRoot == -1) {
                    joinAttrRoot = attr;
                }
                active.add(attr);
            }
        }

        if (joinAttrRoot ==-1)
            throw new RuntimeException("No join attribute found.");

        return new queryInfo(record, active);
    }

    private static String literalToString(Expression expr) {
        if (expr instanceof StringValue sv) {
            return sv.getValue();
        }
        if (expr instanceof LongValue lv) {
            return Long.toString(lv.getValue());
        }
        if (expr instanceof DoubleValue dv) {
            return Double.toString(dv.getValue());
        }
        return expr.toString();
    }
}
