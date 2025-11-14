package org;

import java.util.*;


public class ChowM2D extends AbstractSynopsis {
    CountMin[] singleCMs;
    CountMinHashColumns[] doubleCMs;
    ChowLiu.Edge[] edges;
    HashMap<ChowLiu.Edge, Integer> invEdges;
    private final int d;
    private final int w;
    private final int root_cl;
    List<List<Integer>> adjMatrix;
    public ChowM2D(int numAttributes, int ram, List<ChowLiu.Edge> chowLiu) {
        setting = "ChowM2D";
        this.ram = ram;
        int[] params = getParams(ram, numAttributes);
        this.d = params[0];
        this.w = params[1];
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

        // inverse map of edges to find position
        invEdges = new HashMap<>();
        for (int i = 0; i < edges.length; i++) {
            invEdges.put(edges[i], i);
        }

        // adjacency matrix:
        buildGraph();
    }

    private int[] getParams(int ram, int numAttributes) {
        int d = 3;
        // number of CMs needed: numAttributes + numAttributes - 1
        int numCMs = numAttributes + numAttributes - 1;
        int w = ram / (d * numCMs * 4);
        System.out.println("Chow M2D has parameters d: " + d + " w: " +(int) Math.sqrt(w));

        return new int[]{d,(int) Math.sqrt(w)};
    }

    private int getRoot(Set<Integer> subtree) {

        for (Integer i: subtree) {
            if  (adjMatrix.get(i).size() == 1) {
                return i;
            }
        }
        if (root == -1) {
            throw new RuntimeException("Cannot find root for chow liu");
        }
        return root;


        for (Integer i: subtree) {
            boolean leafInSubtree = true;
            for (int j: adjMatrix.get(i)) {
                if (subtree.contains(j)){
                    leafInSubtree = false;
                    break;
                }
            }
            if (leafInSubtree) {
                return i;
            }
        }
        throw new RuntimeException("Can't find root node for " + adjMatrix);
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
        ArrayList<Integer> active = new ArrayList<>();
        ArrayList<Integer> notActive = new ArrayList<>();
        for (int i = 0; i < record.length; i++) {
            if (record[i] != -1) {
                active.add(i);
            } else  {
                notActive.add(i);
            }
        }

        if (active.isEmpty()) {
            return N;
        }

        if (active.size() == 1) {
            return singleCMs[active.get(0)].query(record[active.get(0)]);
        }

//        if (predicates.size() == 2) {
//            if (predicates.get(0) > predicates.get(1)) {
//                throw new RuntimeException("Predicates not sorted");
//            }
//            int pos = invEdges.get(new ChowLiu.Edge(predicates.get(0), predicates.get(1)));
//            return doubleCMs[pos].query(record[predicates.get(0)], record[predicates.get(1)]);
//        }

        // Need ChowLiu tree.
        Set<Integer> sub = minimalSubtree(active);

        int root_cl = getRoot(sub);
        List<ChowLiu.Edge> f = buildFactorization(sub, root_cl, adjMatrix);

        // query the pairwise CMs to get to estimate.

        return queryFactors(f, record, notActive);
    }

    private Set<Integer> getAttrNotInPredicatesButInChowPath(List<ChowLiu.Edge> f, ArrayList<Integer> notActive) {
        Set<Integer> attrNotInPredicatesButInChowPath = new HashSet<>();
        for (Integer attrNotActive : notActive) {
            for (ChowLiu.Edge edge : f) {
                if (edge.u == attrNotActive) {
                    attrNotInPredicatesButInChowPath.add(edge.u);
                }
                if (edge.v == attrNotActive) {
                    attrNotInPredicatesButInChowPath.add(edge.v);
                }
            }
        }
        return attrNotInPredicatesButInChowPath;
    }

    private int queryFactors(List<ChowLiu.Edge> f, int[] record, ArrayList<Integer> notActive) {
        Set<Integer> attrNotInPredicatesButInChowPath = getAttrNotInPredicatesButInChowPath(f, notActive);

        int sumW = 1;
        // assume for now attrNotInPredicatesButInChowPath.size() == 1
        if (attrNotInPredicatesButInChowPath.size() > 1) {
            throw new RuntimeException("Multiple attributes not in pred but in CL path.");
        }
        int attr = -1;
        if (attrNotInPredicatesButInChowPath.size() == 1) {
            attr = attrNotInPredicatesButInChowPath.iterator().next();
            sumW = w;
        }

        int minEstimate = Integer.MAX_VALUE;
        for (int j = 0; j < d; j++) {
            double sum = 0;
            for (int i = 0; i < sumW; i++) {
                double product_p = 0;
                for (int cl_idx = 0; cl_idx < f.size(); cl_idx++) {
                    ChowLiu.Edge edge = f.get(cl_idx);
                    if (edge.u == edge.v) {
                        // single node:
                        if (edge.v == attr) {
                            product_p = singleCMs[attr].queryCell(j, i) / N;
                        } else {
                            product_p = (double) singleCMs[edge.u].query(record[edge.u]) / N;
                        }

                    } else if (edge.u == attr) {
                        double jointFreq = doubleCMs[invEdges.get(new ChowLiu.Edge(edge.u, edge.v))].queryCellL(j, i, record[edge.v]) / N;
                        product_p = getSum(f, record, product_p, attr, j, i, cl_idx, edge, jointFreq);
                    } else if (edge.v == attr) {
                        double jointFreq = doubleCMs[invEdges.get(new ChowLiu.Edge(edge.u, edge.v))].queryCellR(j, record[edge.v], i) / N;
                        product_p = getSum(f, record, product_p, attr, j, i, cl_idx, edge, jointFreq);
                    } else {
                        int pos = invEdges.getOrDefault(new ChowLiu.Edge(edge.u, edge.v), -1);
                        if (pos == -1) {
                            throw new RuntimeException("Cannot find pos for edge " + edge.u + " " + edge.v);
                        }
                        double jointFreq = (double) doubleCMs[pos].query(record[edge.u], record[edge.v]) / N;
                        int condAttr = getCondAttr(edge.u, edge.v, f.get(cl_idx - 1));
                        double denominator = (double) singleCMs[condAttr].query(record[condAttr]) / N;
                        product_p *= jointFreq / denominator;
                    }
                }
                if (product_p > 0) {
                    sum += product_p;
                }
            }
            int estimate = (int) (N * sum);
            if (estimate < minEstimate) {
                minEstimate = estimate;
            }
        }
        if (minEstimate == Integer.MAX_VALUE) {
            System.out.println("inspect");
        }
        return minEstimate;
    }

    private double getSum(List<ChowLiu.Edge> f, int[] record, double sum, int attr, int j, int i, int cl_idx, ChowLiu.Edge edge, double jointFreq) {
        int condAttr = getCondAttr(edge.u, edge.v, f.get(cl_idx - 1));
        double denominator;
        if (condAttr == attr) {
            denominator = singleCMs[condAttr].queryCell(j, i) / N;
        } else {
            denominator = (double) singleCMs[condAttr].query(record[condAttr]) / N;
        }
        sum *= jointFreq / denominator;
        return sum;
    }

    private int getCondAttr(int u, int v, ChowLiu.Edge edgeParent) {
        // which of u and v is in edgeParent. It can only be one
        int condAttr = -1;
        ArrayList<Integer> edgeAttrs = new ArrayList<>();
        edgeAttrs.add(edgeParent.u);
        edgeAttrs.add(edgeParent.v);
        for (Integer edgeAttr : edgeAttrs) {
            if (edgeAttr == u) {
                condAttr = u;
                break;
            }
            if (edgeAttr == v) {
                condAttr = v;
                break;
            }
        }
        if (condAttr == -1) {
            throw new RuntimeException("Cannot find condAttr for edge " + edgeParent.u + " " + edgeParent.v);
        }
        return condAttr;

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
    private Set<Integer> minimalSubtree(ArrayList<Integer> active) {

        Set<Integer> subtree = new HashSet<>();
        for (int i = 0; i < active.size(); i++) {
            for (int j = i + 1; j < active.size(); j++) {
                subtree.addAll(findPath(active.get(i), active.get(j)));
            }
        }
        return subtree;
    }

    static List<ChowLiu.Edge> buildFactorization(Set<Integer> subtree, int root, List<List<Integer>> adj) {
        List<ChowLiu.Edge> factors = new ArrayList<>();
        boolean[] visited = new boolean[adj.size()];
        Deque<Integer> stack = new ArrayDeque<>();
        // if root not in query, try something else of subtree
        if (subtree.contains(root)) {
            stack.push(root);
            visited[root] = true;
            factors.add(new  ChowLiu.Edge(root, root));
        } else {
            throw new RuntimeException("Subtree has no root.");
        }


        while (!stack.isEmpty()) {
            int u = stack.pop();
            for (int v : adj.get(u)) {
                if (subtree.contains(v) && !visited[v]) {
                    visited[v] = true;
                    stack.push(v);
                    factors.add(new ChowLiu.Edge(Math.min(v,u), Math.max(v,u)));
                }
            }
        }
        return factors;
    }


}
