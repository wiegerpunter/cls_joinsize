package org.synopses.factorJoin;


import org.dataset.SynopsisQuery;
import org.experiments.RunJoinExperiment;

import java.util.*;

public class aSH {
    // Adaptive Sample and Hold. Sketch for estimating count of elements in a stream.
    int numAttrs; // Double[] is of form c_i, tau_i, u_i, z_i
    HashMap<LongArrayKey, double[]> sketch; // For long[] records, Double[] is of form c_i, tau_i, u_i, z_i
    HashMap<ObjectArrayKey, double[]> sketchObj; // For Record objects, Double[] is of form c_i, tau_i, u_i, z_i
    //HashMap<long[], Double[]> buffer; // Double[] is of form c_i, tau_i, u_i, z_i

    int count;

    int size; // The number of records seen so far.

    final int maxSize; // The maximum size of the sketch.
    int sketchSize; // The size of the sketch.
    //    int bufferSize; // The size of the buffer.
    double bufferFactor; // The factor by which the buffer is smaller than the sketch.
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
    public final HashMap<Integer, RunJoinExperiment.Buckets> joinKeyBinMap = new HashMap<>();

    class LongArrayKey {
        private final long[] data;

        LongArrayKey(long[] data) {
            this.data = data;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof LongArrayKey)) return false;
            return Arrays.equals(data, ((LongArrayKey) o).data);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }
    }

    class ObjectArrayKey {
        private final Object[] data;

        ObjectArrayKey(Object[] data) {
            this.data = data;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof ObjectArrayKey)) return false;
            return Arrays.equals(data, ((ObjectArrayKey) o).data);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(data);
        }
    }


    public aSH(String t, String[] columns, Map<SynopsisQuery.ColumnKey, String> globalColumnToJoinKey,
               HashMap<String, RunJoinExperiment.Buckets> globalJoinKeyBins,
               boolean useNoCast, int numAttributes, int[] parameters, int repetition,
               boolean useBufferInQuery, double ingestBuffer) {
        this.maxSize = parameters[0] + parameters[1];
        this.numAttrs = numAttributes;
        this.sketchSize = parameters[0];
        if (useNoCast) {
            this.sketch = new HashMap<>(parameters[0] + parameters[1]);
        } else {
            this.sketchObj = new HashMap<>(parameters[0] + parameters[1]);
        }
        //this.buffer = new HashMap<long[], Double[]>(parameters[1]);
        this.bufferFactor = ingestBuffer;
        this.seed = repetition;
        this.useBufferInQuery = useBufferInQuery;
        int firstSeed = 12 + seed;
        int secondSeed = 10 + seed;
        rn = new Random(firstSeed);
        rn1 = new Random(secondSeed);
        this.sameRecCounter = 0;

        this.table = t;
        this.columns = columns;
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

    }

    @Override
    public void add(long[] record) {
        ingest(record, 1);
        count++;
    }

    @Override
    public void add(Record record) {
        ingest(record, 1);
        count++;
    }


    public void ingest(long[] record, int sign) {
        long[] sampleRec = new long[record.length - 1];
        System.arraycopy(record, 1, sampleRec, 0, record.length - 1);
        LongArrayKey sampleRecKey = new LongArrayKey(sampleRec);
//        List<Object> sampleRecord = new ArrayList<>();
//        for (int i = 1; i < record.length(); i++) {
//            //sampleRecord[i - 1] = record[i];
//            sampleRecord.add(record.getValue(i));
//        };

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

    public void ingest(Record record, int sign) {

        Object[] sampleRec = new Object[record.length() - 1];
        for (int i = 0; i < record.length() - 1; i++) {
            sampleRec[i] = record.getValue(i + 1);
        }
        ObjectArrayKey sampleRecKey = new ObjectArrayKey(sampleRec);

//        List<Object> sampleRecord = new ArrayList<>();
//        for (int i = 1; i < record.length(); i++) {
//            //sampleRecord[i - 1] = record[i];
//            sampleRecord.add(record.getValue(i));
//        };

        if (sketchObj.containsKey(sampleRecKey)) { //add  && sketch.get(sampleRecord) != null if broken
            sameRecCounter++;
            // increment the count of the record
            double[] recordValue = sketchObj.get(sampleRecKey);
            recordValue[0] += sign;
            if (recordValue[0] <= 0) {
                sketchObj.remove(sampleRecKey);
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
                sketchObj.put(sampleRecKey, recordValue);
                size++;
                if (size >= maxSize) {
                    ejectObj();
                }
            }
        }
    }

    private void eject() {
        List<Map.Entry<LongArrayKey, double[]>> entries = new ArrayList<>(sketch.entrySet());

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
            LongArrayKey key = entries.get(i).getKey();

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

    private void ejectObj() {
        int mapSize = sketchObj.size();

        double[] Tis = new double[mapSize];

        Map<ObjectArrayKey, Double> TisMap = new HashMap<>();
//        List<Map.Entry<ObjectArrayKey, double[]>> entries = new ArrayList<>(sketchObj.entrySet());


        int numToEject = size - sketchSize;

        PriorityQueue<Double> bs_minvalues = new PriorityQueue<>(Comparator.reverseOrder());//Ascending: we want the min values to eject. Comparator.reverseOrder());

        int idx = 0;
        for (Map.Entry<ObjectArrayKey, double[]> entry : sketchObj.entrySet()) {
            double[] sample = entry.getValue();
            sample[2] = rn.nextDouble(0, 1);
            sample[3] = Math.log(rn1.nextDouble(0, 1));
            double Ti = Math.max(sample[1] / sample[2], sample[0] / (-sample[3]));
            Tis[idx++] = Ti;
            TisMap.put(entry.getKey(), Ti);
            bs_minvalues.add(Ti);
            if (bs_minvalues.size() > numToEject) { // We only want to eject numToEject records with the smallest values. If we have more, we eject the largest ones.
                bs_minvalues.poll(); // Eject the largest value.
            }
        }
        if (bs_minvalues.size() != numToEject || bs_minvalues.isEmpty()) {
            throw new RuntimeException("Error in ejecting records from aSH");
        }
        double tstar = bs_minvalues.peek(); // The largest value in the set of smallest values.
        Iterator<Map.Entry<ObjectArrayKey, double[]>> iterator = sketchObj.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<ObjectArrayKey, double[]> entry = iterator.next();
            ObjectArrayKey key = entry.getKey();
            double[] sample = entry.getValue();
            double Ti = TisMap.get(key);

            if (Ti <= tstar) { // tstar is threshold for ejection.
                iterator.remove();
                size--;
            } else { // Update the records that are not ejected.
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

    @Override
    public int query(long[] query, int numPreds) {
        throw new UnsupportedOperationException("Not supported anymore.");
    }

    @Override
    public int query(Record query, int numPreds, QueryInfo queryInfo) {
        // go over all records in the reservoir and check if they match the query
        if (!useBufferInQuery && size > sketchSize) {
            ejectObj();
        }

        double queryResultCount = 0;
        Set<ObjectArrayKey> keys = sketchObj.keySet();
        for (ObjectArrayKey key : keys) {
            boolean match = true;
            for (int j = 0; j < query.length(); j++) {
                if (!RecordUtils.flexibleEquals(query.getValue(j),-1) && !RecordUtils.flexibleEquals(query.getValue(j),key.data[j])) {
                    match = false;
                    break;
                }
            }
            if (match) {
                // check if queryResultCount is not overflow of max int.
                if (Integer.MAX_VALUE - queryResultCount < (int) (sketchObj.get(key)[0]) + sketchObj.get(key)[1]) {
                    queryResultCount = Integer.MAX_VALUE;
                    System.out.println("OVERFLOW IN ASH");
                    break;
                }
                queryResultCount+= sketchObj.get(key)[0] + sketchObj.get(key)[1];
                // sketch.get(key)[0] is c_i and sketch.get(key)[1] is tau_i
            }
        }
        // scale result by the size of the reservoir
        return (int) queryResultCount;// * (Math.max((double) count /size, 1)));
    }

    @Override
    public int query(long[] query, int numPreds, QueryInfo queryInfo) {
        // go over all records in the reservoir and check if they match the query
        if (!useBufferInQuery && size > sketchSize) {
            eject();
        }

        double queryResultCount = 0;
        Set<LongArrayKey> keys = sketch.keySet();
        for (LongArrayKey key : keys) {
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

    @Override
    public int query(long[] query, int numPreds, int unionSize, QueryInfo CMRow) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int rangeQuery(long[] minrange, long[] maxrange) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void reset() {
        sketchObj = new HashMap<>(parameters[0] + parameters[1]);
        size = 0;
        count = 0;
    }

    @Override
    public void delete(long[] r) {
        ingest(r, -1);
        count--;
    }

    @Override
    public void delete(Record r) {
        ingest(r, -1);
        count--;
    }

    @Override
    public int getFilledKSamples() {
        return 0;
    }

    public long getMemoryUsage() {
        //double v = 1 / (1 - bufferFactor);
        return Formulas.ramASH(maxSize, numAttrs);
//        return (long) maxSize * 32 * (numAttrs + 5); // size * (size of record + size of Double array + Tis array)
    }

    public void printParams() {
        System.out.println("Adaptive Sample and Hold with size: " + maxSize);
    }
}
