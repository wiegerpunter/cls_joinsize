package org;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
record Edge(int a, int b) {

}

public class NaiveMin extends AbstractNaiveMin {
    CountMin[] singleCMs;
    CountMin[] doubleCMs;
    Edge[] edges;
    HashMap<Edge, Integer> invEdges;
    public int d;
    public int w;

    public NaiveMin(int numAttributes, int d, int w) {
        setting = "NaiveMin";
        this.d = d;
        this.w = w;
        this.numAttributes = numAttributes;
        singleCMs = new CountMin[numAttributes];
        for (int i = 0; i < singleCMs.length; i++) {
            singleCMs[i] = new CountMin(d, (int) Math.sqrt(w));
        }
        doubleCMs = new CountMin[(numAttributes*numAttributes - numAttributes)/2];
        for (int i = 0; i < doubleCMs.length; i++) {
            doubleCMs[i] = new CountMin(d, w);
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

    public int getd() {
        return d;
    }
    public int getw() {
        return w;
    }

    @Override
    public void add(int[] record) {
        N += 1;
        for (int i = 0; i < numAttributes; i++) {
            singleCMs[i].add(record[i]);
        }
        for (int i = 0; i < edges.length; i++) {
            doubleCMs[i].add(record[edges[i].a()], record[edges[i].b()]);
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
