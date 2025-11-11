package org;

import java.util.Arrays;

public class ChowMin {
    int numAttributes;
    CountMin[] singleCMs;
    CountMin[] doubleCMs;
    int[][] edges;
    int N = 0;

    public ChowMin(int numAttributes, double[][] corr) {
        this.numAttributes = numAttributes;
        int d = 7;
        int w = 28;
        singleCMs = new CountMin[numAttributes];
        for (int i = 0; i < singleCMs.length; i++) {
            singleCMs[i] = new CountMin(d, w);
        }

        edges = buildTree(corr);
        doubleCMs = new CountMin[edges.length];
        for (int i = 0; i < doubleCMs.length; i++) {
            doubleCMs[i] = new CountMin(d, w*w);
        }
    }

    public void add(int[] record) {
        N += 1;
        for (int i = 0; i < numAttributes; i++) {
            singleCMs[i].add(record[i]);
        }
        for (int i = 0; i < edges.length; i++) {
            doubleCMs[i].add(record[edges[i][0]], record[edges[i][1]]);
        }
    }

    private double marginal(int attribute, int attrValue) {
        return (double) singleCMs[attribute].query(attrValue) / N;
    }

    private double conditional(int attr1, int attrCond, int attr1Value, int attrCondValue) {
        double denominator = marginal(attrCond, attrCondValue);
        int edge = -1;
        boolean edgeCondFirst = true;
        for (int e = 0; e < edges.length; e++) {
            if  (edges[e][0] == attr1) {
                if (edges[e][1] == attrCond) {
                    edge = e;
                    edgeCondFirst = false;
                }
            }
            if  (edges[e][1] == attr1) {
                if (edges[e][0] == attrCond) {
                    edge = e;
                }
            }
        }
        if (edge == -1) {
            throw new RuntimeException("Edge Not found for attributes " + attr1 + ", " + attrCond);
        }
        double numerator;
        if (edgeCondFirst) {
           numerator = (double) doubleCMs[edge].query(attrCondValue, attr1Value) / N;
        } else {
            numerator = (double) doubleCMs[edge].query( attr1Value, attrCondValue) / N;
        }
        return numerator / denominator;
    }

    public int query(int[] record) {
        double prob = marginal(0,record[0])
                * conditional( 1, 0,record[1],record[0])
                * conditional( 2, 1,record[2],record[1])
                * conditional( 3, 2,record[3],record[2]);

//        double prob = marginal(0,record[0])
//                * conditional( 1, 0,record[1],record[0]);
        return (int) (prob * N);
    }

    public static int[][] buildTree(double[][] corrMatrix) {
        int n = corrMatrix.length;
        double[][] weights = new double[n][n];

        // 1. Compute MI-equivalent weights
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) weights[i][j] = 0;
                else weights[i][j] = -0.5 * Math.log(1 - corrMatrix[i][j] * corrMatrix[i][j]);
            }
        }

        // 2. Compute maximum spanning tree (Prim’s algorithm)
        boolean[] visited = new boolean[n];
        double[] maxWeight = new double[n];
        int[] parent = new int[n];
        Arrays.fill(maxWeight, Double.NEGATIVE_INFINITY);
        Arrays.fill(parent, -1);

        maxWeight[0] = 0;
        for (int count = 0; count < n - 1; count++) {
            // find unvisited node with highest weight
            double max = Double.NEGATIVE_INFINITY;
            int u = -1;
            for (int i = 0; i < n; i++) {
                if (!visited[i] && maxWeight[i] > max) {
                    max = maxWeight[i];
                    u = i;
                }
            }

            visited[u] = true;

            // update neighbors
            for (int v = 0; v < n; v++) {
                if (!visited[v] && weights[u][v] > maxWeight[v]) {
                    maxWeight[v] = weights[u][v];
                    parent[v] = u;
                }
            }
        }

        // 3. Return edges (parent[v], v)
        int[][] edges = new int[n - 1][2];
        int idx = 0;
        for (int v = 1; v < n; v++) {
            edges[idx++] = new int[]{parent[v], v};
        }
        // print edges
        for (int[] v: edges) {
                System.out.println(Arrays.toString(v));
        }


        return edges;
    }

    public void memoryUsage() {
        int ram = 0;
        for (CountMin singleCM : singleCMs) {
            ram += singleCM.memory();
        }
        for (int i = 0; i < edges.length; i++) {
            ram += doubleCMs[i].memory();
        }
        System.out.println("Memory usage ChowMin: " + ram + " bytes");
    }

}
