package org.synopses.ChowMin;

import org.synopses.AbstractSynopsis;
import org.utils.Edge;

import java.util.*;

import static org.synopses.ChowMin.TWODCM.mix;


public class ChowM2DVarElim extends AbstractSynopsis {
    /*
     Pairwise summary for edges in ChowLiu tree. plus currently a single CM for every attribute.
     Pairwise summary updates counter at cell [h_x(x), h_y(y)].
     */
    HashMap<Edge, TWODCM> scheme;
    List<Edge> edges;
    private final int d;
    private final int w;
    private final int[][] seeds;
    private final int[][] hashes;
    private final int mask;
    List<List<Integer>> adjMatrix;

    public ChowM2DVarElim(int numAttributes, int depth, int ram, List<Edge> chowLiu) {
        this.setting = "ChowM2DVarElim";
        this.ram = ram;
        this.numAttributes = numAttributes;
        this.edges = chowLiu;
        int[] params = getParams(ram, depth, numAttributes);
        this.d = params[0];
        this.w = params[1];
        scheme = new HashMap<>();
        for (Edge edge : chowLiu) {
            scheme.put(edge, new TWODCM(d, w, edge));
        }
        // adjacency matrix:
        buildGraph();
        seeds = new int[numAttributes][d];
        for (int i = 0; i < numAttributes; i++) {
            for (int j = 0; j < seeds[i].length; j++) {
                seeds[i][j] = j + i;
            }
        }
        hashes = new int[numAttributes][d];
        mask = w - 1;
    }

    public void computeHashes(int attrValue, int[] hashes, int[] seeds) {

        for (int i = 0; i < d; i++) {
            hashes[i] = mix(attrValue, seeds[i]) & mask;
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

        for (Edge edge : edges) {
            scheme.get(edge).addHashed(hashes[edge.getS()], hashes[edge.getT()]);
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
        boolean hasLatent = attrsInPath.size() > active.size();
        // query the pairwise CMs to get to estimate.

        return queryFactors(f, record, attrsInPath, hasLatent);
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

    @Override
    public int getNumSamples() {
        return 0;
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
    private int queryFactors(List<Edge> f, int[] record, HashSet<Integer> attrsInPath, boolean hasLatent) {
        int minEstimate = Integer.MAX_VALUE;
        Edge rootEdge = getRootEdge(f);
        for (int row = 0; row < d; row++) {
            int estimate = beliefPropagation(row, record, attrsInPath, f, rootEdge, hasLatent);
            if (estimate < minEstimate) {
                minEstimate = estimate;
            }
        }
        return minEstimate;
    }

    private Edge getRootEdge(List<Edge> f) {
        return f.get(0);
    }

    private int beliefPropagation(int row, int[] record, Set<Integer> subtree, List<Edge> factors, Edge rootEdge, boolean hasLatent) {
        if (hasLatent) {
            return beliefPropagationWithLatent(row, record, subtree, adjMatrix, rootEdge);
        } else {
            return beliefPropagationNoLatent(row, record, factors, rootEdge);
        }
    }

    private int beliefPropagationNoLatent(
            int row,
            int[] record,
            List<Edge> factors,
            Edge rootEdge
    ) {
        int ru = rootEdge.getS();
        int rv = rootEdge.getT();

        // hashed positions
        int hru = TWODCM.hash(record[ru], ru, row, w);
        int hrv = TWODCM.hash(record[rv], rv, row, w);

        // root joint (raw)
        TWODCM rootCM = scheme.get(rootEdge);
        double result = rootCM.getCount(row, ru, rv, hru, hrv);

        // multiply all other conditionals
        for (Edge e : factors) {

            if (e.equals(rootEdge)) continue;

            int u = e.getS();
            int v = e.getT();

            int hu = TWODCM.hash(record[u], u, row, w);
            int hv = TWODCM.hash(record[v], v, row, w);

            TWODCM cm = scheme.get(e);

            double joint = cm.getCount(row, u, v, hu, hv);

            // denominator = sum_x M(x, hv)  (column sum)
            double denom = 0.0;
            for (int x = 0; x < w; x++) {
                denom += cm.getCount(row, u, v, hu, x);
            }

            if (denom == 0) denom = 1e-12;

            result *= (joint / denom);
        }

        return (int) result;
    }

    private int beliefPropagationWithLatent(
            int row,
            int[] record,
            Set<Integer> subtree,
            List<List<Integer>> adj,
            Edge rootEdge
        ) {
        int ru = rootEdge.getS();
        int rv = rootEdge.getT();

        // messages: key = u*(numAttributes) + v
        double[][][] messages = new double[numAttributes][numAttributes][];

        // evidence vectors phi[u][x]
        double[][] phi = new double[numAttributes][w];
        for (int u = 0; u < numAttributes; u++) {
            if (!subtree.contains(u)) continue;

            if (record[u] == -1) {
                Arrays.fill(phi[u], 1.0);
            } else {
                phi[u][TWODCM.hash(record[u], u, row, w)] = 1.0;
            }
        }

        dfsComputeMessages(row, ru, -1, subtree, adj, phi, messages);

        // compute inc_ru (excluding rv)
        double[] incRu = incR(subtree, adj, ru, rv, messages, phi);

        // compute inc_rv (excluding ru)
        double[] incRv = incR(subtree, adj, rv, ru, messages, phi);

        // combine at base edge
        TWODCM cm = scheme.get(new Edge(Math.min(ru, rv), Math.max(ru, rv)));
        double total = 0.0;

        for (int xru = 0; xru < w; xru++) {
            for (int xrv = 0; xrv < w; xrv++) {
                int joint = cm.getCount(row, ru, rv, xru, xrv); // implement
                total += incRu[xru] * joint * incRv[xrv];
            }
        }

        return (int) total;
    }

    private double[] incR(Set<Integer> subtree, List<List<Integer>> adj, int r, int rv, double[][][] messages, double[][] phi) {
        double[] incR = Arrays.copyOf(phi[r], w);
        for (int nbr : adj.get(r)) {
            if (!subtree.contains(nbr) || nbr == rv) continue;
            double[] msg = messages[nbr][r];
            for (int x = 0; x < w; x++) incR[x] *= msg[x];
        }
        return incR;
    }

    private void dfsComputeMessages(
            int row,
            int u,
            int parent,
            Set<Integer> subtree,
            List<List<Integer>> adj,
            double[][] phi,
            double[][][] messages
        ) {
        for (int nbr : adj.get(u)) {
            if (nbr == parent) continue;
            if (!subtree.contains(nbr)) continue;
            dfsComputeMessages(row, nbr, u, subtree, adj, phi, messages);
        }

        if (parent == -1) return;

        // incoming[x_u] = phi[u][x_u] * Π child_msg[x_u]
        double[] incoming = Arrays.copyOf(phi[u], w);

        for (int nbr : adj.get(u)) {
            if (nbr == parent) continue;
            if (!subtree.contains(nbr)) continue;

            double[] msg = messages[nbr][u];
            for (int xu = 0; xu < w; xu++) incoming[xu] *= msg[xu];
        }

        // outgoing[x_parent] = Σ_xu incoming[xu] * P(x_parent|xu)
        double[] outgoing = new double[w];

        TWODCM cm = scheme.get(new Edge(Math.min(u, parent), Math.max(u, parent)));

        // compute row sums for conditional denom[xu]
        double[] denom = new double[w];
        for (int xp = 0; xp < w; xp++) {
            double sum = 0.0;
            for (int xu = 0; xu < w; xu++) {
                sum += cm.getCount(row, u, parent, xu, xp);
            }
            denom[xp] = (sum == 0.0) ? 1e-12 : sum;
        }

        // outgoing[xp] = Σ_xu incoming[xu] * (M[xu,xp]/denom[xu])
        for (int xp = 0; xp < w; xp++) {
            double sum = 0.0;
            for (int xu = 0; xu < w; xu++) {
                double joint = cm.getCount(row, u, parent, xu, xp);
                sum += incoming[xu] * (joint / denom[xp]);
            }
            outgoing[xp] = sum;
        }
        messages[u][parent] = outgoing;
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
//            factors.add(new Edge(root, root));
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

    private int[] getParams(int ram, int d, int numAttributes) {
        // number of CMs needed: numAttributes + numAttributes - 1
        int numCMs = Math.max(numAttributes - 1, 1);
        int cells = ram / 4;
        int perCM = cells / (d * numCMs);
        int width = (int) Math.sqrt(perCM);
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
            int numInSubtree = 0;
            for (int j: adjMatrix.get(i)) {
                if (subtree.contains(j)){
                    numInSubtree++;
                }
            }
            if (numInSubtree == 1) {
                return i;
            }
        }
        throw new RuntimeException("Can't find root node for " + adjMatrix + ", subtree: " + subtree);
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
