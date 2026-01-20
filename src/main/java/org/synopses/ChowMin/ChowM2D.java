package org.synopses.ChowMin;

import org.synopses.AbstractSynopsis;
import org.utils.Edge;

import java.util.*;
import java.util.stream.IntStream;


public class ChowM2D extends AbstractSynopsis {
    /*
     Pairwise summary for edges in ChowLiu tree. plus currently a single CM for every attribute.
     Pairwise summary updates counter at cell [h_x(x), h_y(y)].
     */
    HashMap<Edge, TWODCM> scheme;
    List<Edge> edges;
    private final int d;
    private final int w;
    List<List<Integer>> adjMatrix;

    public ChowM2D(int numAttributes, int ram, List<Edge> chowLiu) {
        this.setting = "ChowM2D";
        this.ram = ram;
        this.numAttributes = numAttributes;
        this.edges = chowLiu;
        int[] params = getParams(ram, numAttributes);
        this.d = params[0];
        this.w = params[1];
        scheme = new HashMap<>();
        for (Edge edge : chowLiu) {
            scheme.put(edge, new TWODCM(d, w, edge));
        }
        // adjacency matrix:
        buildGraph();
    }

    public void add(int[] record) {
        N += 1;
        for (Edge edge : edges) {
            scheme.get(edge).add(record[edge.getS()], record[edge.getT()]);
        }
    }

    public int query(int[] record) {
        ArrayList<Integer> active = new ArrayList<>();
        ArrayList<Integer> notActive = new ArrayList<>();
        checkActive(active, notActive, record);

        if (active.isEmpty()) {
            return N;
        }

        if (active.size() == 1) {
            return marginal(active.get(0), record[active.get(0)]);
        }

        // Need ChowLiu tree.
        Set<Integer> sub = minimalSubtree(active);

        int root_cl = getRoot(sub);
        List<Edge> f = buildFactorization(sub, root_cl, adjMatrix);
        HashSet<Integer> attrsInPath = getAttrsInPath(f);
        // query the pairwise CMs to get to estimate.

        return queryFactors(f, record, notActive, attrsInPath);
    }

    public int numLatent(int[] record) {
        ArrayList<Integer> active = new ArrayList<>();
        ArrayList<Integer> notActive = new ArrayList<>();
        checkActive(active, notActive, record);

        if (active.isEmpty()) {
            return 0;
        }

        if (active.size() == 1) {
            return 0;
        }

        // Need ChowLiu tree.
        Set<Integer> sub = minimalSubtree(active);

        int root_cl = getRoot(sub);
        List<Edge> f = buildFactorization(sub, root_cl, adjMatrix);
        HashSet<Integer> attrsInPath = getAttrsInPath(f);
        Set<Integer> latentAttrs = getLatentAttrs(attrsInPath, notActive);
        if (latentAttrs.isEmpty()) {
            return 0;
        } else {
            return latentAttrs.size();
        }
    }

    private void checkActive(ArrayList<Integer> active, ArrayList<Integer> notActive, int[] record) {
        for (int i = 0; i < record.length; i++) {
            if (record[i] != -1) {
                active.add(i);
            } else  {
                notActive.add(i);
            }
        }
    }

    private HashSet<Integer> getAttrsInPath(List<Edge> f) {
        HashSet<Integer> attrs= new HashSet<Integer>(f.size());
        for (Edge edge : f){
            attrs.add(edge.getS());
            attrs.add(edge.getT());
        }
        return attrs;
    }

    private int marginal(Integer attr, int predicate) {
        Edge e = getEdge(attr);
        if (scheme.containsKey(e)) {
            return scheme.get(e).queryMarginal(predicate, attr);
        } else {
            throw new RuntimeException("No pairwise relation for attribute " + attr);
        }
    }

    Edge getEdge(Integer attr) {
        int secAttr;
        if (!adjMatrix.get(attr).isEmpty()) { // check if it's empty.
            secAttr = adjMatrix.get(attr).get(0);
        } else {
            throw new RuntimeException("No adjacent attributes to attribute " + attr);
        }

        return new Edge(Math.min(attr,secAttr), Math.max(attr,secAttr));
    }


    int queryMarginalCodomain(int condAttr, int[][] k,  boolean hasLatent, int row) {
        Edge e = getEdge(condAttr);
        return scheme.get(e).queryMarginalCodomain(condAttr, k, hasLatent, row);
    }

    Set<Integer> getLatentAttrs(HashSet<Integer> attrsInPath, ArrayList<Integer> notActive) {
        Set<Integer> latentAttrs = new HashSet<>();
        for (Integer attrNotActive : notActive) {
            for (Integer attr: attrsInPath) {
                if (Objects.equals(attr, attrNotActive)) {
                    latentAttrs.add(attr);
                }
            }
        }
        return latentAttrs;
    }

    ArrayList<AttributeDimension> attributeDimensions = new ArrayList<>();
    private int queryFactors(List<Edge> f, int[] record, ArrayList<Integer> notActive, HashSet<Integer> attrsInPath) {
        Set<Integer> latentAttrs = getLatentAttrs(attrsInPath, notActive);
//        int attrsInQuery = attrsInPath.size() + latentAttrs.size();
        int minEstimate = Integer.MAX_VALUE;
        constructDimensions(record, attrsInPath, latentAttrs);
        int numDimensions = attributeDimensions.size();
        int[] indices = new int[numDimensions];
        if (latentAttrs.isEmpty()) {
            minEstimate = pathQuery(0, numDimensions, indices, latentAttrs, f);
        } else {
            for (int row = 0; row < d; row++) {
                int estimate = pathQuery(row, numDimensions, indices, latentAttrs, f);
                if (estimate < minEstimate) {
                    minEstimate = estimate;
                }
            }
        }
        return minEstimate;
    }

    private int pathQuery(int row, int numDimensions, int[] indices, Set<Integer> latentAttrs, List<Edge> f) {
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
            for (int cl_idx = 0; cl_idx < f.size(); cl_idx++) {
                Edge edge = f.get(cl_idx);
                if (edge.getS() == edge.getT()) {
                    if (latentAttrs.contains(edge.getS())) {
                        throw new RuntimeException("Latent attribute is root");
                    }
                    Edge edgeOfRoot = getEdge(edge.getS());
                    product_p = (double) scheme.get(edgeOfRoot).queryMarginalCodomain(edge.getS(), k, false, row) / N;
                } else {
                    boolean hasLatent = edgeHasLatent(edge, latentAttrs);
                    double jointFreq = (double) scheme.get(edge).queryCodomain(k, edge, hasLatent, row) / N;
                    int condAttr = getCondAttr(edge.getS(), edge.getT(), f.get(cl_idx - 1));
                    double denominator = (double) queryMarginalCodomain( condAttr, k, latentAttrs.contains(condAttr), row) / N;
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

    private boolean edgeHasLatent(Edge edge, Set<Integer> latentAttrs) {
        // Decide whether edge has a latent variable.
        if (latentAttrs.contains(edge.getS())) {
            return true;
        } else return latentAttrs.contains(edge.getT());
    }

    void constructDimensions(int[] record, HashSet<Integer> attrsInPath, Set<Integer> latentAttrs) {
        attributeDimensions = new ArrayList<>();
        for (int i = 0; i < numAttributes; i++) {
            boolean isLatent = latentAttrs.contains(i);
            boolean isInPath = attrsInPath.contains(i);
            if (!isInPath && !isLatent) {
                continue;
            }
            int[] codomain;
            if (!isLatent) {
                codomain = getCodomain(i, record[i]); // format int[d rows]
                int[][] valuesCodomain = new int[d][1];
                for (int j = 0; j < codomain.length; j++) {
                    valuesCodomain[j] = new int[]{codomain[j]};
                }
                attributeDimensions.add(new AttributeDimension(i, valuesCodomain));
            } else {
                int[][] valuesCodomain = new int[d][w];
                for (int j = 0; j < valuesCodomain.length; j++) {
                    valuesCodomain[j] = IntStream.range(0,valuesCodomain[j].length).toArray();
                }
                attributeDimensions.add(new AttributeDimension(i, valuesCodomain));
            }
        }
    }

    private int[] getCodomain(int attr, int predicate) {
        // find right 2DCM
        Edge e = getEdge(attr);

        // return int[] codomain
        return scheme.get(e).getCodomain(attr, predicate);
    }

    int getCondAttr(int u, int v, Edge edgeParent) {
        // which of u and v is in edgeParent. It can only be one
        int condAttr = -1;
        ArrayList<Integer> edgeAttrs = new ArrayList<>();
        edgeAttrs.add(edgeParent.getS());
        edgeAttrs.add(edgeParent.getT());
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
            throw new RuntimeException("Cannot find condAttr for edge " + edgeParent.getS() + " " + edgeParent.getT());
        }
        return condAttr;

    }

    public int memoryUsage() {
        int ram = 0;
        for (Edge edge : edges) {
            ram += scheme.get(edge).memory();
        }
        return ram;
    }


     private void buildGraph() {
        adjMatrix = new ArrayList<>(numAttributes);
        for (int i = 0; i < numAttributes; i++) adjMatrix.add(new ArrayList<>());
        for (Edge e : edges) {
            adjMatrix.get(e.getS()).add(e.getT());
            adjMatrix.get(e.getT()).add(e.getS());
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
    // Includes "hidden" latent variables like node 1 between 0 and 2.
    private Set<Integer> minimalSubtree(ArrayList<Integer> active) {

        Set<Integer> subtree = new HashSet<>();
        for (int i = 0; i < active.size(); i++) {
            for (int j = i + 1; j < active.size(); j++) {
                subtree.addAll(findPath(active.get(i), active.get(j)));
            }
        }
        return subtree;
    }

    static List<Edge> buildFactorization(Set<Integer> subtree, int root, List<List<Integer>> adj) {
        List<Edge> factors = new ArrayList<>();
        boolean[] visited = new boolean[adj.size()];
        Deque<Integer> stack = new ArrayDeque<>();
        // if root not in query, try something else of subtree
        if (subtree.contains(root)) {
            stack.push(root);
            visited[root] = true;
            factors.add(new Edge(root, root));
        } else {
            throw new RuntimeException("Subtree has no root.");
        }

        while (!stack.isEmpty()) {
            int u = stack.pop();
            for (int v : adj.get(u)) {
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
        int d = 3;
        // number of CMs needed: numAttributes + numAttributes - 1
        int numCMs = numAttributes - 1;
        int cells = ram / 4;
        int perCM = cells / (d * numCMs);
        int width = (int) Math.sqrt(perCM);
        width = Integer.highestOneBit(width);
        System.out.println("Chow M2D has parameters d: " + d + " w: " +width);

        return new int[]{d,width};
    }

    private int getRoot(Set<Integer> subtree) {

        for (Integer i: subtree) {
            if  (adjMatrix.get(i).size() == 1) {
                return i;
            }
        }

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

    public double getN() {
        return N;
    }
}
