package org;

import org.utils.Edge;
import org.utils.Record;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ChowLiu {

    public static class EdgeWithWeight {
        int u, v;
        double weight;
        EdgeWithWeight(int u, int v, double w) {
            this.u = u;
            this.v = v;
            this.weight = w;
        }
    }

    static class DSU {
        int[] parent, rank;
        DSU(int n) {
            parent = new int[n];
            rank = new int[n];
            for (int i = 0; i < n; i++) parent[i] = i;
        }
        int find(int x) {
            if (parent[x] != x) parent[x] = find(parent[x]);
            return parent[x];
        }
        boolean union(int u, int v) {
            int rx = find(u), ry = find(v);
            if (rx == ry) return false;
            if (rank[rx] < rank[ry]) parent[rx] = ry;
            else if (rank[rx] > rank[ry]) parent[ry] = rx;
            else { parent[ry] = rx; rank[rx]++; }
            return true;
        }
    }

    /**
     * Build maximum spanning tree edges using MI matrix.
     * @param MI symmetric matrix of mutual information.
     * @return list of edges (u,v) in the Chow–Liu tree.
     */
    public static List<Edge> chowLiu(double[][] MI) {
        int n = MI.length;
        List<EdgeWithWeight> edges = new ArrayList<>();

        for (double[] doubles : MI) {
            edges.add(new EdgeWithWeight((int) doubles[0], (int) doubles[1], doubles[2]));
        }
        // sort by descending MI
        edges.sort((a, b) -> Double.compare(b.weight, a.weight));

        DSU dsu = new DSU(n);
        List<Edge> tree = new ArrayList<>();

        for (EdgeWithWeight e : edges) {
            if (dsu.union(e.u, e.v)) {
                tree.add(new  Edge(e.u, e.v));
                if (tree.size() == n - 1) break;
            }
        }
        return tree;
    }


    static List<Edge> computeExactMutualInformation(int[][] data) {
        // goal: compute exact chow liu tree.

        double[][] MIs = new double[(data[0].length*data[0].length-data[0].length)/2][3];
        int count = 0;
        for (int i = 0; i < data[0].length; i++) {
            for (int j = i + 1; j < data[0].length; j++) {
                MIs[count] = new double[]{i,j,0};
                count++;
            }
        }

        // loop over MIs and compute mutual information for columns 0, 1 and update 2.

        for (int i = 0; i < MIs.length; i++) {
            MIs[i][2] = computeMI(data, (int) MIs[i][0], (int) MIs[i][1]);
            System.out.println("MI " + MIs[i][0] + " , " + MIs[i][1] +" = " + MIs[i][2]);
        }

        List<Edge> chowLiuTree = chowLiu(MIs);
        for (Edge e : chowLiuTree) {
            System.out.printf("Edge (%d, %d)", e.getU(), e.getV());
        }
        // get max span tree for ChowLiu:
        return chowLiuTree;

    }
    private static double computeMI(int[][] data, int v1, int v2) {
        int N = data.length;
        HashMap<Integer, Integer> uniqueV1 = new HashMap<>();
        HashMap<Integer, Integer> uniqueV2 = new HashMap<>();

        HashMap<org.utils.Record, Integer> unique = new HashMap<>();

        for (int[] datum : data) {
            int v1Val = datum[v1];
            int v2Val = datum[v2];
            uniqueV1.put(v1Val, uniqueV1.getOrDefault(v1Val, 0) + 1);
            uniqueV2.put(v2Val, uniqueV2.getOrDefault(v2Val, 0) + 1);
            org.utils.Record v1v2 = new org.utils.Record(new int[]{v1Val, v2Val});
            unique.put(v1v2, unique.getOrDefault(v1v2, 0) + 1);
        }
        double mi= 0;
        for (Integer v1Val : uniqueV1.keySet()) {
            for  (Integer v2Val : uniqueV2.keySet()) {
                org.utils.Record v1v2 = new Record(new int[]{v1Val, v2Val});
                double p_v1_v2 = (double) unique.getOrDefault(v1v2, 0) / N; // # co-occurences of v1, v2
                double p_v1 = (double) uniqueV1.get(v1Val) / N; // # occurences of v1
                double p_v2 = (double) uniqueV2.get(v2Val) / N; // # occurences of v2
                if (p_v1_v2 > 0)
                    mi += p_v1_v2 * Math.log(p_v1_v2 / (p_v1 * p_v2))/Math.log(2) ;
            }
        }
        return mi;
    }

    // --- Example usage ---
    public static void main(String[] args) {
        // Example MI matrix (symmetric, zero diagonal)
        double[][] MI = {
                {0, 0.90475275, 1.73688301, 2.70838433},
                {0.90475275, 0, 0.24704629, 0.39246054},
                {1.73688301, 0.24704629, 0, 0.75886671},
                {2.70838433, 0.39246054, 0.75886671, 0}
        };

        List<Edge> tree = chowLiu(MI);
        for (Edge e : tree) {
            System.out.printf("Edge (%d, %d)", e.getU(), e.getV());
        }
    }
}