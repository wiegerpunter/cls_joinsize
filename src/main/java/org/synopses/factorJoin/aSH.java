package org.synopses.factorJoin;


import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import org.dataset.SynopsisQuery;
import org.experiments.RunJoinExperiment;
import org.utils.Formulas;

import java.util.*;

public class aSH {
    // Adaptive Sample and Hold. Sketch for estimating count of elements in a stream.
    int numAttrs; // Double[] is of form c_i, tau_i, u_i, z_i
    HashMap<IntArrayKey, double[]> sketch; // For long[] records, Double[] is of form c_i, tau_i, u_i, z_i
//    HashMap<ObjectArrayKey, double[]> sketchObj; // For Record objects, Double[] is of form c_i, tau_i, u_i, z_i
    //HashMap<long[], Double[]> buffer; // Double[] is of form c_i, tau_i, u_i, z_i

    int count; // The number of records seen so far.

    int size;

    final int maxSize; // The maximum size of the sketch.
    int sketchSize; // The size of the sketch.// The factor by which the buffer is smaller than the sketch.
    Random rn;
    Random rn1;
    boolean useBufferInQuery;

    public int numRemovals = 0;
    public int sameRecCounter;

    // SynopsisRefactor vars
    final int seed;

    // join specific vars
    private final String table;
    private final String[] columns;
    public final boolean[] isJoinKey;
    private int numJoinKeys;
    public final HashMap<Integer, RunJoinExperiment.Buckets> joinKeyBinMap = new HashMap<>();

    class IntArrayKey {
        private final int[] data;
        private final HashMap<Integer, Integer> jkBins;

        IntArrayKey(int[] data, HashMap<Integer, Integer> jkBins) {
            this.data = data;
            this.jkBins = jkBins;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof IntArrayKey)) return false;
            return Arrays.equals(data, ((IntArrayKey) o).data);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }
    }

//    class ObjectArrayKey {
//        private final Object[] data;
//
//        ObjectArrayKey(Object[] data) {
//            this.data = data;
//        }
//
//        @Override
//        public boolean equals(Object o) {
//            if (!(o instanceof ObjectArrayKey)) return false;
//            return Arrays.equals(data, ((ObjectArrayKey) o).data);
//        }
//
//        @Override
//        public int hashCode() {
//            return Arrays.hashCode(data);
//        }
//    }


    public aSH(String t, String[] columns, Map<SynopsisQuery.ColumnKey, String> globalColumnToJoinKey,
               HashMap<String, RunJoinExperiment.Buckets> globalJoinKeyBins,
               boolean useNoCast, int numAttributes, int[] parameters, int repetition,
               boolean useBufferInQuery) {
        this.maxSize = parameters[0] + parameters[1];
        this.numAttrs = numAttributes;
        this.sketchSize = parameters[0];
        if (useNoCast) {
            this.sketch = new HashMap<>(parameters[0] + parameters[1]);
        } else {
            throw new RuntimeException("Not implemented object cast");
        }
        //this.buffer = new HashMap<long[], Double[]>(parameters[1]);
        this.seed = repetition;
        this.useBufferInQuery = useBufferInQuery;
        int firstSeed = 12 + seed;
        int secondSeed = 10 + seed;
        rn = new Random(firstSeed);
        rn1 = new Random(secondSeed);
        this.sameRecCounter = 0;

        this.table = t;
        this.columns = columns;
        numJoinKeys=0;
        isJoinKey = new boolean[numAttributes];
        for (int i = 0; i < numAttributes; i++) {
            String globJK = globalColumnToJoinKey.getOrDefault(new SynopsisQuery.ColumnKey(t,columns[i]),"");
            if (!globJK.isEmpty()) {
                numJoinKeys++;
                isJoinKey[i] = true;
                RunJoinExperiment.Buckets joinKeyBins = globalJoinKeyBins.get(globJK);
                joinKeyBinMap.put(i, joinKeyBins);
            }
            // else no join key.
        }

    }

    public void addArr(int[] record) {
        ingest(record, 1);
        count++;
    }

    public void delete(int[] r) {
        ingest(r, -1);
        count--;
    }


    public void ingest(int[] record, int sign) {
        // add bin as extra column;
        HashMap<Integer, Integer> jkBins = new HashMap<>();
        for (int i = 0; i < record.length; i++) {
            if (isJoinKey[i]) {
                int bin = getBin(record[i], joinKeyBinMap.get(i));
                jkBins.put(i, bin);
            }
        }
        IntArrayKey sampleRecKey = new IntArrayKey(record, jkBins);

        if (sketch.containsKey(sampleRecKey)) { //add  && sketch.get(sampleRecord) != null if broken
            sameRecCounter++;
            // increment the count of the record
            double[] recordValue = sketch.get(sampleRecKey);
            recordValue[0] += sign;
            if (recordValue[0] <= 0) {
                sketch.remove(sampleRecKey);
                size--;
                numRemovals++;
            }
            // no need to re-put recordValue
        } else {
            if (sign > 0) {
                // add the record to the sketch
                double[] recordValue = new double[4];
                recordValue[0] = sign; // c_i
                recordValue[1] = 0; // tau_i
                // u_i and z_i are not set here, but in EjectOne(S)
                sketch.put(sampleRecKey, recordValue);
                size++;
                if (size >= maxSize) {
                    eject();
                }
            }
        }
    }

    private void eject() {
        List<Map.Entry<IntArrayKey, double[]>> entries = new ArrayList<>(sketch.entrySet());

        double[] Tis = new double[entries.size()];
        int numToEject = size - sketchSize;

        PriorityQueue<Double> bs_minvalues = new PriorityQueue<>(Comparator.reverseOrder());//Ascending: we want the min values to eject. Comparator.reverseOrder());

        for (int i = 0; i < entries.size(); i++) {
            double[] sample = entries.get(i).getValue();
            sample[2] = rn.nextDouble(0, 1);
            sample[3] = Math.log(rn1.nextDouble(0, 1));
            Tis[i] = Math.max(sample[1] / sample[2], sample[0] / (-sample[3]));
            bs_minvalues.add(Tis[i]);
            if (bs_minvalues.size() > numToEject) { // We only want to eject numToEject records with the smallest values. If we have more, we eject the largest ones.
                bs_minvalues.poll(); // Eject the largest value.
            }
        }
        if (bs_minvalues.size() != numToEject || bs_minvalues.isEmpty()) {
            throw new RuntimeException("Error in ejecting records from aSH");
        }
        double tstar = bs_minvalues.peek(); // The largest value in the set of smallest values.

        for (int i = 0; i < entries.size(); i++) {
            IntArrayKey key = entries.get(i).getKey();

            if (Tis[i] <= tstar) { // tstar is threshold for ejection.
                sketch.remove(key);
                size--;
            } else { // Update the records that are not ejected.
                double[] sample = entries.get(i).getValue();
                if (sample[1] <= tstar) {
                    if (tstar * sample[2] > sample[1]) {
                        //sketch.get(key)[0] += tstar * sample[3];
                        sample[0] += tstar * sample[3];
                        if (tstar * sample[3] > 0) {
                            System.out.println("Error in aSH eject");
                        }
                    }
                    sample[1] = tstar;
                }
            }
        }
    }
    public int query(int[] query, int numPreds) {
        // go over all records in the reservoir and check if they match the query
        if (!useBufferInQuery && size > sketchSize) {
            eject();
        }

        double queryResultCount = 0;
        Set<IntArrayKey> keys = sketch.keySet();
        for (IntArrayKey key : keys) {
            boolean match = true;
            for (int j = 0; j < query.length; j++) {
                if (query[j] != -1 && query[j] != key.data[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                // check if queryResultCount is not overflow of max int.
                if (Integer.MAX_VALUE - queryResultCount < (int) (sketch.get(key)[0]) + sketch.get(key)[1]) {
                    queryResultCount = Integer.MAX_VALUE;
                    System.out.println("OVERFLOW IN ASH");
                    break;
                }
                queryResultCount += sketch.get(key)[0] + sketch.get(key)[1];
                // sketch.get(key)[0] is c_i and sketch.get(key)[1] is tau_i
            }
        }
        return (int) queryResultCount;// * (Math.max((double) count /size, 1)));
    }

    public double queryBucketMarginal(int pkColumn, int bucketValue) {
        double queryResultCount = 0;
        if (!isJoinKey[pkColumn]) {
            throw new RuntimeException("Wrong use of marginal query, not on join key.");
        }
        Set<IntArrayKey> keys = sketch.keySet();
        for (IntArrayKey key : keys) {
            if (bucketValue == -1) {
                continue;
            }
            if (bucketValue == key.jkBins.get(pkColumn)) {
                queryResultCount += sketch.get(key)[0] + sketch.get(key)[1];
            }
        }
        return queryResultCount;

    }

    public double[] queryBucketAssignment(int[] query, HashMap<Integer, Integer> bucketPerAttribute,
                                          HashSet<Integer> active, boolean pkHasPred) {
        if (!useBufferInQuery && size > sketchSize) {
            eject();
        }
        // filtered and unfiltered
        // go over all records in the reservoir and check if they match the query
        double queryResultCount = 0;
        double unfilteredQueryResult = 0;

        Set<IntArrayKey> keys = sketch.keySet();
        for (IntArrayKey key : keys) {
            boolean match = true;
            boolean matchWOPredicates = true;
            for (int j = 0; j < query.length; j++) {
                if (query[j] == -1) {

                    // But if this is an active join key, we still
                    // need to restrict it to the requested bucket.
                    if (isJoinKey[j] && active.contains(j)) {
                        int bucket = bucketPerAttribute.get(j);

                        if (key.jkBins.get(j) != bucket) {
                            match= false;
                            matchWOPredicates = false;
                            break;
                        }
                    }
                    continue;
                }
                // Explicit predicate.
                if (key.data[j] != query[j]) {
                    match = false;
                }
            }
            if (match) {
                // check if queryResultCount is not overflow of max int.
                if (Integer.MAX_VALUE - queryResultCount < (int) (sketch.get(key)[0]) + sketch.get(key)[1]) {
                    queryResultCount = Integer.MAX_VALUE;
                    System.out.println("OVERFLOW IN ASH");
                    break;
                }
                queryResultCount += sketch.get(key)[0] + sketch.get(key)[1];
                // sketch.get(key)[0] is c_i and sketch.get(key)[1] is tau_i
            }
            if (matchWOPredicates) {
                // check if queryResultCount is not overflow of max int.
                if (Integer.MAX_VALUE - unfilteredQueryResult < (int) (sketch.get(key)[0]) + sketch.get(key)[1]) {
                    unfilteredQueryResult = Integer.MAX_VALUE;
                    System.out.println("OVERFLOW IN ASH");
                    break;
                }
                unfilteredQueryResult += sketch.get(key)[0] + sketch.get(key)[1];
                // sketch.get(key)[0] is c_i and sketch.get(key)[1] is tau_i
            }
        }
        // scale result by the size of the reservoir
//        double scale = count < size ? 1.0 : (double) count / size;

        if (pkHasPred) {
            return new double[] {
                    Math.max(1.0, unfilteredQueryResult),
                    queryResultCount
            };
        }
        return new double[] {
                queryResultCount
        };
    }

    public int getMemoryUsage() {
        //double v = 1 / (1 - bufferFactor);
        return Formulas.ramASHWithBins(maxSize, numAttrs, numJoinKeys);
//        return (long) maxSize * 32 * (numAttrs + 5); // size * (size of record + size of Double array + Tis array)
    }

    public void printParams() {
        System.out.println("Adaptive Sample and Hold with size: " + maxSize);
    }

    // Join size bucket query functions



    // Join size specific helper functions

    public record queryInfo(int[] record, HashSet<Integer> active) {

    }
    public queryInfo queryInfo(List<EqualsTo> selectionPredicatesPerTable, List<SynopsisQuery.ColumnKey> unqColumns) {
        int[] record = new int[numAttrs];
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
                record[attr] = Integer.parseInt(value);
                // keeping this value for predicates on JKs not used in Join.
                //getBin(Integer.parseInt(value), joinKeyBinMap.get(attr));
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

    public int getN() {
        return count;
    }

    public int getNumColumns() {
        return numAttrs;
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
