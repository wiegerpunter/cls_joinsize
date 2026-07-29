package org.synopses;

import org.utils.Edge;

import java.util.ArrayList;
import java.util.HashMap;

public class NaiveMin extends AbstractSynopsis {
    /*
     Pairwise summary for all attributes x attributes, plus a single CM for every attribute.
     Pairwise Summary is CM with a composite hash key.
     */


    CountMin[] singleCMs;
    CountMin[] doubleCMs;
    Edge[] edges;
    HashMap<Edge, Integer> invEdges;
    public int d;
    public int w;

    public NaiveMin(int numAttributes, int ram) {
        setting = "NaiveMin";
        this.ram = ram;
        int[] param = getPars(ram, numAttributes);
        this.d = param[0];
        this.w = param[1];
        this.numAttributes = numAttributes;
        singleCMs = new CountMin[numAttributes];
        for (int i = 0; i < singleCMs.length; i++) {
            singleCMs[i] = new CountMin(d, (int) Math.sqrt(w), i);
        }
        doubleCMs = new CountMin[(numAttributes*numAttributes - numAttributes)/2];
        for (int i = 0; i < doubleCMs.length; i++) {
            doubleCMs[i] = new CountMin(d, w, i);
        }
        edges = new Edge[(numAttributes*numAttributes - numAttributes)/2];
        int count=0;
        for (int i = 0; i < numAttributes; i++) {
            for (int j = i + 1; j < numAttributes; j++) {
                edges[count++] = new Edge(i, j);
            }
        }

        // inverse map of edges to find position
        invEdges = new HashMap<>();
        for (int i = 0; i < edges.length; i++) {
            invEdges.put(edges[i], i);
        }

    }

    private int[] getPars(int ram, int numAttributes) {
        int d = 3;
        // for every attr combination, make pairwise composite CM
        int numPairwiseCMs = (numAttributes * numAttributes - numAttributes)/2;
        // maxWidth = ram / (d * numPairwiseCMs * 4);
        int w = ram / (d * numPairwiseCMs * 4);
        System.out.println("Naive min has parameters d: " + d + " w: " + w);
        return new int[]{d, w};
    }

    public int getd() {
        return d;
    }
    public int getw() {
        return w;
    }

    @Override
    public int numLatent(int[] query) {
        return 0;
    }

    @Override
    public int getNumSamples() {
        return 0;
    }

    @Override
    public void addIntArr(int[] record) {
        N += 1;
        for (int i = 0; i < numAttributes; i++) {
            singleCMs[i].add(record[i]);
        }
        for (int i = 0; i < edges.length; i++) {
            doubleCMs[i].add(record[edges[i].getS()], record[edges[i].getT()]);
        }
    }

    @Override
    public int query(int[] record) {
        ArrayList<Integer> predicates = new ArrayList<>();
        for (int i = 0; i < record.length; i++) {
            if (record[i] != -1) {
                predicates.add(i);
            }
        }

        if (predicates.isEmpty()) {
            return N;
        }

        if (predicates.size() == 1) {
            return singleCMs[predicates.get(0)].query(record[predicates.get(0)]);
        }

        if (predicates.size() == 2) {
            if (predicates.get(0) > predicates.get(1)) {
                throw new RuntimeException("Predicates not sorted");
            }
            int pos = invEdges.get(new Edge(predicates.get(0), predicates.get(1)));
            return doubleCMs[pos].query(record[predicates.get(0)], record[predicates.get(1)]);
        }
        throw new RuntimeException("Not supported yet for naive CM");
    }

    @Override
    public int memoryUsage() {
        int ram = 0;
        for (CountMin singleCM : singleCMs) {
            ram += singleCM.memory();
        }
        for (int i = 0; i < edges.length; i++) {
            ram += doubleCMs[i].memory();
        }
       return ram;
    }

}
