package org.synopses.ChowMin;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.synopses.AbstractSynopsis;
import org.utils.Edge;

import java.util.*;

import static org.ChowLiu.chowLiuWithPredicates;
import static org.synopses.ChowMin.TWODCM.mix;


public class ChowMinAllPairs extends AbstractSynopsis {
    /*
     Pairwise summary for edges in ChowLiu tree. plus currently a single CM for every attribute.
     Pairwise summary updates counter at cell [h_x(x), h_y(y)].
     */
    TWODCM[] sketches;
    List<Edge> edges;
    private final int d;
    private final int w;
    private final int[][] seeds;
    private final int[][] hashes;
    private final int mask;
    private final double[][] MIs;

    public ChowMinAllPairs(int numAttributes, int depth, int ram, double[][] MIs) {
        this.setting = "ChowM2DAllPairs";
        this.ram = ram;
        this.numAttributes = numAttributes;
        this.MIs = MIs;
        int[] params = getParams(ram, numAttributes, depth);
        this.d = params[0];
        this.w = params[1];
        sketches = new TWODCM[numAttributes*(numAttributes-1)/2];
        for (int a1 = 0; a1 < numAttributes-1; a1++) { // summary for every pair in upper triangle
            for (int a2 = a1+1; a2<numAttributes; a2++) {
                sketches[sketch_idx(a1, a2)] = new TWODCM(d, w, a1, a2);
            }
        }
        seeds = new int[numAttributes][d];
        for (int i = 0; i < numAttributes; i++) {
            for (int j = 0; j < seeds[i].length; j++) {
                seeds[i][j] = j + i;
            }
        }
        hashes = new int[numAttributes][d];
        mask = w - 1;
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



    private int sketch_idx_first(int attr) {
        if (attr<0 || attr >= numAttributes) {
            throw new IllegalArgumentException("Out of range");
        }
        if (attr == 0) {
            return 0;
        }
        return attr - 1;
    }

    public void computeHashes(int attrValue, int[] hashes, int[] seeds) {

        for (int i = 0; i < d; i++) {
            hashes[i] = mix(attrValue, seeds[i], mask);
        }
    }
    public void add(int[] record) {
        N += 1;
        for (int att = 0; att < numAttributes; att++) {
            int[] h = hashes[att];
            int[] s = seeds[att];
            int v = record[att];
            computeHashes(v, h, s);
        }

        for (int a1 = 0; a1 < numAttributes-1; a1++) { // summary for every pair in upper triangle
            for (int a2 = a1 + 1; a2 < numAttributes; a2++) {
                int[] ha1 = hashes[a1];
                int[] ha2 = hashes[a2];
                int sid = sketch_idx(a1, a2);
                for (int j = 0; j < d; j++) {
                    sketches[sid].CMR[sketches[sid].idx(j,ha1[j],ha2[j])] +=1;
                }
            }
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
        List<Edge> f = buildFactorization(attrsInPath, root_cl, numAttributes, MIs);
        // query the pairwise CMs to get to estimate.

        return queryFactors(f, record, attrsInPath);
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
        return sketches[sketch_idx_first(attr)].queryMarginal(predicate, attr);
    }
    int queryMarginalCodomain(int condAttr, int[][] k) {
        return sketches[sketch_idx_first(condAttr)].queryMarginalCodomainWOLatent(condAttr, k);
    }

    ArrayList<AttributeDimension> attributeDimensions = new ArrayList<>();
    private int queryFactors(List<Edge> f, int[] record, HashSet<Integer> attrsInPath) {
        constructDimensions(record, attrsInPath);
        int numDimensions = attributeDimensions.size();
        int[] indices = new int[numDimensions];
        return pathQuery(numDimensions, indices, f);
    }

    private int pathQuery(int numDimensions, int[] indices, List<Edge> f) {
        double sum = 0;
        int[][] k = new int[d][numAttributes];
        while (true) {
            for (int i = 0; i < numDimensions; i++) {
                AttributeDimension dim = attributeDimensions.get(i);
                int codomainIndex = indices[i];
                for (int j = 0; j < d; j++) {
                    k[j][dim.attribute] = dim.valuesPerCodomain[j][codomainIndex];
                }
            }

            double product_p = 0;
            for (Edge edge : f) {
                int s = edge.getS();
                int t = edge.getT();
                if (s == t) {
                    product_p = (double) sketches[sketch_idx_first(s)].queryMarginalCodomainWOLatent(s, k);
                } else {
                    double jointFreq = (double) sketches[sketch_idx(edge.getS(), edge.getT())].queryCodomainWOLatent(k, edge);
                    double denominator = (double) queryMarginalCodomain(s, k);
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
        if (sum == 0) {
            System.out.println("q 0");
        }
        return (int) (sum);
    }

    void constructDimensions(int[] record, HashSet<Integer> attrsInPath) {
        attributeDimensions = new ArrayList<>();
        for (int i = 0; i < numAttributes; i++) {
            boolean isInPath = attrsInPath.contains(i);
            if (!isInPath) {
                continue;
            }
            int[] codomain;
            codomain = getCodomain(i, record[i]); // format int[d rows]
            int[][] valuesCodomain = new int[d][1];
            for (int j = 0; j < codomain.length; j++) {
                valuesCodomain[j] = new int[]{codomain[j]};
            }
            attributeDimensions.add(new AttributeDimension(i, valuesCodomain));
        }
    }

    private int[] getCodomain(int attr, int predicate) {
        return sketches[sketch_idx_first(attr)].getCodomain(attr, predicate);
    }

    public int memoryUsage() {
        int ram = 0;
        for (TWODCM cm : sketches) {
            ram += cm.memory();
        }
        return ram;
    }

    /**
     * Find path between two nodes in a tree using BFS.
     */

    public static List<Edge> buildFactorization(Set<Integer> subtree, int root, int numAttributes, double[][] MIs) {
        List<Edge> factors = new ArrayList<>();
        List<List<Integer>> adj = getChowLiuAdjMatrix(subtree, numAttributes, MIs);

        boolean[] visited = new boolean[adj.size()];
        Deque<Integer> stack = new ArrayDeque<>();
        // if root not in query, try something else of subtree
        //no root in all pairwise
        if (subtree.contains(root)) {
            stack.push(root);
            visited[root] = true;
            factors.add(new Edge(root, root));
        } else {
            throw new RuntimeException("Subtree has no root.");
        }


        while (!stack.isEmpty()) {
            int u = stack.pop();
            for (int v :adj.get(u)) {
                if (subtree.contains(v) && !visited[v]) {
                    visited[v] = true;
                    stack.push(v);
                    factors.add(new Edge(Math.min(v,u), Math.max(v,u)));
                }
            }
        }
        return factors;
    }

    private static List<List<Integer>> getChowLiuAdjMatrix(Set<Integer> subtree, int numAttributes, double[][] MIs) {
        List<Edge> chowLiu = chowLiuWithPredicates(MIs, subtree, numAttributes);
        return getLists(chowLiu, numAttributes);
    }

    @NonNull
    static List<List<Integer>> getLists(List<Edge> edges, int numAttributes) {
        List<List<Integer>> adjMatrix = new ArrayList<>(numAttributes);
        for (int i = 0; i < numAttributes; i++) adjMatrix.add(new ArrayList<>());
        if (edges.size()==1){
            adjMatrix.get(edges.get(0).getS()).add(edges.get(0).getT());
        }
        for (Edge e : edges) {
            adjMatrix.get(e.getS()).add(e.getT());
            adjMatrix.get(e.getT()).add(e.getS());
        }
        return adjMatrix;
    }

    private int[] getParams(int ram, int numAttributes, int d) {
        // number of CMs needed: numAttributes + numAttributes - 1
        int numCMs = numAttributes * numAttributes/2 - numAttributes/2;
        int cells = ram / 4;
        int perCM = cells / (d * numCMs);
        int width = (int) Math.sqrt(perCM);
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
