package org.synopses.ChowMin;

import org.synopses.AbstractSynopsis;
import org.synopses.CountSketch;
import org.utils.Edge;

import java.util.*;

import static org.ChowLiu.chowLiuWithPredicates;
import static org.synopses.ChowMin.ChowMinAllPairs.getLists;


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
    private final double[][] MIs;

    public ChowMinCountSketchAllPairs(int numAttributes, int depth, int ram, double[][] MIs) {
        this.setting = "ChowM2DCountAllPairs";
        this.ram = ram;
        this.numAttributes = numAttributes;
        this.MIs = MIs;
        int[] params = getParams(ram, numAttributes, depth);
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
        }
        for (int i = 0; i < numAttributes; i++) {
            margSketches[i].add(record[i]);
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
        List<Edge> f = ChowMinAllPairs.buildFactorization(attrsInPath, root_cl, numAttributes, MIs);
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
        return pathQuery(record, f);
    }

    private int pathQuery(int[] queryRecord, List<Edge> f) {
        double sum = 0;
//        int[][] k = new int[d][numAttributes];
        double product_p = 0;
        for (Edge edge : f) {
            int s = edge.getS();
            int t = edge.getT();
            if (s == t) {
                product_p = margSketches[s].query(queryRecord[s]);
            } else {
                double jointFreq = sketches[sketch_idx(s, t)].query(queryRecord[s], queryRecord[t]);
                double denominator = margSketches[s].query(queryRecord[s]);
                if (denominator == 0) {
                    denominator = 1e-12;
                }
                product_p *= jointFreq / denominator;
            }
        }
        if (product_p > 0) {
            sum += product_p;
        }
        return (int) (sum);
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

    private int[] getParams(int ram, int numAttributes, int d) {
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
