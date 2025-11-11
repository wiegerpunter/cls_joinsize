package org;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChowLiu {

    public static class Edge {
        int u, v;
        Edge(int u, int v) {
            this.u = u;
            this.v = v;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true; // same object
            if (!(obj instanceof ChowLiu.Edge other)) return false;
            if (this.u == other.u && this.v == other.v) return true;
            return false;
        }

        @Override
        public int hashCode() {
            return u + v;
        }

    }
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
        boolean union(int x, int y) {
            int rx = find(x), ry = find(y);
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
            System.out.printf("Edge (%d, %d): MI=%.6f%n", e.u, e.v);
        }
    }
}