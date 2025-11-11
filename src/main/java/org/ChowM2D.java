package org;

import java.util.*;


public class ChowM2D extends AbstractNaiveMin {
    CountMin[] singleCMs;
    CountMinHashColumns[] doubleCMs;
    ChowLiu.Edge[] edges;
    HashMap<ChowLiu.Edge, Integer> invEdges;
    private int d;
    private int w;
    private int root_cl;
    List<List<Integer>> adjMatrix;
    public ChowM2D(int numAttributes, int d, int w, List<ChowLiu.Edge> chowLiu) {
        setting = "ChowM2D";
        this.d = d;
        this.w = w;
        this.numAttributes = numAttributes;
        singleCMs = new CountMin[numAttributes];
        for (int i = 0; i < singleCMs.length; i++) {
            singleCMs[i] = new CountMin(d, w);
        }
        doubleCMs = new CountMinHashColumns[chowLiu.size()];
        for (int i = 0; i < doubleCMs.length; i++) {
            doubleCMs[i] = new CountMinHashColumns(d, w);
        }
        edges = new ChowLiu.Edge[chowLiu.size()];
        for (int i = 0; i < edges.length; i++) {
            edges[i] = chowLiu.get(i);
        }
        root_cl = getRoot();

        // inverse map of edges to find position
        // adjacency matrix:
        buildGraph();

        invEdges = new HashMap<>();
        for (int i = 0; i < edges.length; i++) {
            invEdges.put(edges[i], i);
        }


    }
    private int getRoot() {
        HashMap<Integer, Integer> candidateRoots = new HashMap<>();
        for (ChowLiu.Edge edge : edges) {
            candidateRoots.put(edge.u, candidateRoots.getOrDefault(edge.u,0) +1);
            candidateRoots.put(edge.v, candidateRoots.getOrDefault(edge.v,0) +1);
        }
        int root = -1; // first candidate with count 1;
        for (Integer u : candidateRoots.keySet()) {
            if (candidateRoots.get(u) == 1) {
                root = u;
                break;
            }
        }
        if (root == -1) {
            throw new RuntimeException("Cannot find root for chow liu");
        }
        return root;

    }
    public int getd() {
        return d;
    }
    public int getw() {
        return w;
    }

    public void add(int[] record) {
        N += 1;
        for (int i = 0; i < numAttributes; i++) {
            singleCMs[i].add(record[i]);
        }
        for (int i = 0; i < edges.length; i++) {
            doubleCMs[i].add(record[edges[i].u], record[edges[i].v]);
        }
    }

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
            int pos = invEdges.get(new ChowLiu.Edge(predicates.get(0), predicates.get(1)));
            return doubleCMs[pos].query(record[predicates.get(0)], record[predicates.get(1)]);
        }

        // Need ChowLiu tree.
        Set<Integer> sub = minimalSubtree(record);
        List<String> f = buildFactorization(sub, root_cl, adjMatrix);
        System.out.println("Factors: " + f);
        return 1000000;
    }

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


     private void buildGraph() {
        adjMatrix = new ArrayList<>(numAttributes);
        for (int i = 0; i < numAttributes; i++) adjMatrix.add(new ArrayList<>());
        for (ChowLiu.Edge e : edges) {
            adjMatrix.get(e.u).add(e.v);
            adjMatrix.get(e.v).add(e.u);
        }
     }

    /**
     * Find path between two nodes in a tree using BFS.
     */
    private List<Integer> findPath(int start, int end) {
        int n = adjMatrix.size();
        int[] parent = new int[n];
        Arrays.fill(parent, -1);

        Queue<Integer> q = new LinkedList<>();
        q.add(start);
        parent[start] = start;

        while (!q.isEmpty()) {
            int cur = q.poll();
            if (cur == end) break;
            for (int nei : adjMatrix.get(cur)) {
                if (parent[nei] == -1) {
                    parent[nei] = cur;
                    q.add(nei);
                }
            }
        }

        // reconstruct path from end → start
        List<Integer> path = new ArrayList<>();
        if (parent[end] == -1) return path; // no path (should not happen in tree)
        for (int cur = end; cur != start; cur = parent[cur]) {
            path.add(cur);
        }
        path.add(start);
        Collections.reverse(path);
        return path;
    }

    // Find minimal subtree (union of paths among predicates)
    // Includes intermediate "hidden" variables like node 1 between 0 and 2.
    private Set<Integer> minimalSubtree(int[] predicates) {
        List<Integer> active = new ArrayList<>();
        for (int i = 0; i < predicates.length; i++) if (predicates[i] != -1) active.add(i);

        Set<Integer> subtree = new HashSet<>();
        for (int i = 0; i < active.size(); i++) {
            for (int j = i + 1; j < active.size(); j++) {
                subtree.addAll(findPath(active.get(i), active.get(j)));
            }
        }
        return subtree;
    }

    static List<String> buildFactorization(Set<Integer> subtree, int root, List<List<Integer>> adj) {
        List<String> factors = new ArrayList<>();
        boolean[] visited = new boolean[adj.size()];
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(root);
        visited[root] = true;
        factors.add("P(" + root + ")");

        while (!stack.isEmpty()) {
            int u = stack.pop();
            for (int v : adj.get(u)) {
                if (subtree.contains(v) && !visited[v]) {
                    visited[v] = true;
                    stack.push(v);
                    factors.add("P(" + v + " | " + u + ")");
                }
            }
        }
        return factors;
    }


}
