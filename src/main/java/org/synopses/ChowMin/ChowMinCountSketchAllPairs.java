package org.synopses.ChowMin;

import org.synopses.AbstractSynopsis;
import org.synopses.CountSketch;
import org.utils.Edge;

import java.util.*;

import static org.synopses.ChowMin.TWODCM.mix;


public class ChowMinCountSketchAllPairs extends AbstractSynopsis {
    /*
     Pairwise summary for edges in ChowLiu tree. plus currently a single CM for every attribute.
     Pairwise summary updates counter at cell [h_x(x), h_y(y)].
     */
    TWODCount[] sketches;
    CountSketch[] margSketches;
    List<Edge> edges;
    private final int d;
    private final int w;
    private final int[][] seeds;

    public ChowMinCountSketchAllPairs(int numAttributes, int ram, List<Edge> chowLiu) {
        this.setting = "ChowM2DCount";
        this.ram = ram;
        this.numAttributes = numAttributes;
        this.edges = chowLiu;
        int[] params = getParams(ram, numAttributes);
        this.d = params[0];
        this.w = params[1];
        sketches = new TWODCount[numAttributes*(numAttributes-1)/2];
        margSketches = new CountSketch[numAttributes];
        for (int a1 = 0; a1 < numAttributes-1; a1++) { // summary for every pair in upper triangle
            for (int a2 = a1+1; a2<numAttributes; a2++) {
                sketches[sketch_idx(a1, a2)] = new TWODCount(d, w, a1, a2);
            }
        }
        for (int i = 0; i < numAttributes; i++) {
            margSketches[i] = new CountSketch(d,w, i);
        }

        seeds = new int[numAttributes][d];
        for (int i = 0; i < numAttributes; i++) {
            for (int j = 0; j < seeds[i].length; j++) {
                seeds[i][j] = j + i;
            }
        }
    }

    private int sketch_idx(int a1, int a2) {
        if (a1 == a2) {
            throw new IllegalArgumentException("Same attribute.");
        }
        if (a1 > a2) {
            int tmp = a1;
            a1 = a2;
            a2 = tmp;
        }
        return a1 * (numAttributes - 1) - (a1 * (a1-1))/2 + (a2 - a1-1);
    }

    public void add(int[] record) {
        N += 1;

        for (int a1 = 0; a1 < numAttributes-1; a1++) { // summary for every pair in upper triangle
            for (int a2 = a1 + 1; a2 < numAttributes; a2++) {
                int sid = sketch_idx(a1, a2);
                sketches[sid].add(record[a1], record[a2]);
            }
            margSketches[a1].add(record[a1]);
        }
    }

    public int query(int[] record) {
        ArrayList<Integer> active = new ArrayList<>(numAttributes);
        checkActive(active, record);

        if (active.isEmpty()) {
            return N;
        }

        if (active.size() == 1) {
            return marginal(active.get(0), record[active.get(0)]);
        }


        HashSet<Integer> attrsInPath = getAttrsInPath(active);

        int root_cl = active.get(0);
        List<Edge> f = buildFactorization(attrsInPath, root_cl);
        // query the pairwise CMs to get to estimate.

        return queryFactors(f, record);
    }

    public int numLatent(int[] record) {
        return 0;
    }

    @Override
    public int getNumSamples() {
        return 0;
    }

    private void checkActive(ArrayList<Integer> active, int[] record) {
        for (int i = 0; i < record.length; i++) {
            if (record[i] != -1) {
                active.add(i);
            }
        }
    }

    private HashSet<Integer> getAttrsInPath(ArrayList<Integer> active) {
        HashSet<Integer> attrs= new HashSet<Integer>(active.size());
        attrs.addAll(active);
        return attrs;
    }

    private int marginal(Integer attr, int predicate) {
        return margSketches[attr].query(predicate);
    }

    ArrayList<AttributeDimension> attributeDimensions = new ArrayList<>();
    private int queryFactors(List<Edge> f, int[] record) {
//        constructDimensions(record, attrsInPath);
        int numDimensions = attributeDimensions.size();
        int[] indices = new int[numDimensions];
        return pathQuery(record,numDimensions, indices, f);
    }

    private int pathQuery(int[] queryRecord, int numDimensions, int[] indices, List<Edge> f) {
        double sum = 0;
//        int[][] k = new int[d][numAttributes];
        while (true) {
            double product_p = 0;
            for (Edge edge : f) {
                int s = edge.getS();
                int t = edge.getT();
                if (s == t) {
                    product_p = (double) margSketches[s].query(queryRecord[s])/N;
                } else {
                    double jointFreq = (double) sketches[sketch_idx(s, t)].query(queryRecord[s], queryRecord[t]) / N;
                    double denominator = (double) margSketches[s].query(queryRecord[s])/N;
                    product_p *= jointFreq / denominator;
                }
            }
            if (product_p > 0) {
                sum += product_p;
            }

            int pos = numDimensions - 1;
            while (pos >= 0) {
                indices[pos]++;
                if (indices[pos] < attributeDimensions.get(pos).codomainSize) {
                    break;
                }
                indices[pos] = 0;
                pos--;
            }

            if (pos < 0) {
                break; // all combinations exhausted
            }
        }
        return (int) (N * sum);
    }

    public int memoryUsage() {
        int ram = 0;
        for (TWODCount cs: sketches) {
            ram+= d*w*w*4;
        }
        for (CountSketch s : margSketches) {
            ram+= d*w*4;
        }
        return ram;
    }

    /**
     * Find path between two nodes in a tree using BFS.
     */

    List<Edge> buildFactorization(Set<Integer> subtree, int root) {
        List<Edge> factors = new ArrayList<>();
        boolean[] visited = new boolean[numAttributes];
        Deque<Integer> stack = new ArrayDeque<>();
        // if root not in query, try something else of subtree
        //no root in all pairwise
        stack.push(root);
        visited[root] = true;
        factors.add(new Edge(root, root));


        while (!stack.isEmpty()) {
            int u = stack.pop();
            for (int v =0; v < numAttributes; v++) {
                if (subtree.contains(v) && !visited[v]) {
                    visited[v] = true;
                    stack.push(v);
                    factors.add(new Edge(Math.min(v,u), Math.max(v,u)));
                }
            }
        }
        return factors;
    }

    private int[] getParams(int ram, int numAttributes) {
        int d = 5;
        // number of CMs needed: numAttributes + numAttributes - 1
        int numCMs = numAttributes * numAttributes/2 - numAttributes/2 + numAttributes;
        int cells = ram / 4;
        int perCM = cells / (d * numCMs);
        int width = (int) Math.sqrt(perCM);
        width = Integer.highestOneBit(width);
        System.out.println("Chow M All pairs has parameters d: " + d + " w: " +width);

        return new int[]{d,width};
    }

    public int getd() {
        return d;
    }
    public int getw() {
        return w;
    }

    public double getN() {
        return N;
    }
}
