package org.synopses.ChowMin;

import net.sf.jsqlparser.expression.DoubleValue;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.schema.Column;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.dataset.SynopsisQuery;
import org.synopses.AbstractSynopsis;
import org.utils.Edge;

import java.util.*;

import static org.ChowLiu.chowLiuWithPredicates;
import static org.synopses.ChowMin.TWODCM.hash;
import static org.synopses.ChowMin.TWODCM.mix;


public class ChowMinAllPairs extends AbstractSynopsis {
    /*
     Pairwise summary for edges in ChowLiu tree. plus currently a single CM for every attribute.
     Pairwise summary updates counter at cell [h_x(x), h_y(y)].
     */
    TWODCM[] sketches;
    private final int depth;
    HashMap<Integer, Integer> widthMap;
    private final int[][] seeds;
    private final int[][] hashes;
    private double[][] MIs;
    final private String table;
    final public String[] columns;
    final public List<String> joinColumns;
    final private boolean[] isJoinKey;
    private final HashMap<String,List<HeavyHitter>> heavyHitters = new HashMap<>();
    private final HashMap<String, List<String>> hhValues;
    private final boolean sepHHs;
    private final Map<SynopsisQuery.ColumnKey,String> globalColToJoinKey;

    int[] joinKeyWs;
    public ChowMinAllPairs(String table, String[] columns, Map<SynopsisQuery.ColumnKey,String> globalColToJoinKey,
                           int numAttributes, int depth, HashMap<String, Integer> stringWidthMap, int ram, boolean sepHHs, HashMap<String, List<String>> hhValues) {
        this.table = table;
        this.columns = columns;
        this.ram = ram;
        this.numAttributes = numAttributes;
        int[] params = getParams(ram, numAttributes, depth);
        this.depth = params[0];
//        this.width = params[1];
        this.widthMap = getWidthMap(stringWidthMap, columns);
        this.sepHHs = sepHHs;
        this.hhValues = hhValues;
        this.globalColToJoinKey = globalColToJoinKey;

        isJoinKey = new boolean[numAttributes];
        this.joinColumns = getJoinColumns();
        setJoinKeyw();
        sketches = new TWODCM[numAttributes*(numAttributes-1)/2];
        for (int a1 = 0; a1 < numAttributes-1; a1++) { // summary for every pair in upper triangle
            for (int a2 = a1+1; a2<numAttributes; a2++) {
                sketches[sketch_idx(a1, a2)] = new TWODCM(this.depth, this.widthMap.get(a1), this.widthMap.get(a2), a1, a2,
                        getAttrSpecHashCode(a1, globalColToJoinKey, false), getAttrSpecHashCode(a2, globalColToJoinKey, false));
            }
        }
        seeds = new int[numAttributes][this.depth];
        for (int i = 0; i < numAttributes; i++) {
            int attrSpecSeed = getAttrSpecHashCode(i, globalColToJoinKey, false);
            for (int j = 0; j < seeds[i].length; j++) {
                seeds[i][j] = (j+1)*attrSpecSeed;
            }
        }
        hashes = new int[numAttributes][this.depth];
    }

    public ChowMinAllPairs(int ram, int numAttributes, int[] params) {
        this.table = "singleTable";
        this.columns = makeColumnsSingleTable(numAttributes);
        this.hhValues = null;
        this.joinColumns = null;
        this.sepHHs = false;
        this.globalColToJoinKey = null;
        this.ram = ram;
        this.numAttributes = numAttributes;
        this.depth = params[0];
        this.widthMap = makeWidthMapSingleTable(numAttributes, params[1]); // only when using joins

        isJoinKey = new boolean[numAttributes];
        sketches = new TWODCM[numAttributes*(numAttributes-1)/2];
        for (int a1 = 0; a1 < numAttributes-1; a1++) { // summary for every pair in upper triangle
            for (int a2 = a1+1; a2<numAttributes; a2++) {
                sketches[sketch_idx(a1, a2)] = new TWODCM(this.depth, this.widthMap.get(a1), this.widthMap.get(a2), a1, a2,
                        getAttrSpecHashCode(a1, globalColToJoinKey, true), getAttrSpecHashCode(a2, globalColToJoinKey, true));
            }
        }
        seeds = new int[numAttributes][this.depth];
        for (int i = 0; i < numAttributes; i++) {
            int attrSpecSeed = getAttrSpecHashCode(i, globalColToJoinKey, true);
            for (int j = 0; j < seeds[i].length; j++) {
                seeds[i][j] = (j+1)*attrSpecSeed;
            }
        }
        hashes = new int[numAttributes][this.depth];
    }

    private HashMap<Integer, Integer> makeWidthMapSingleTable(int numAttributes, int width) {
        widthMap = new HashMap<>();
        for (int i = 0; i < numAttributes; i++) {
            widthMap.put(i, width);
        }
        return widthMap;
    }

    private String[] makeColumnsSingleTable(int numAttributes) {
        String[] columns = new String[numAttributes];
        for (int i = 0; i < columns.length; i++) {
            columns[i] = String.valueOf(i);
        }
        return columns;
    }

    private HashMap<Integer, Integer> getWidthMap(HashMap<String, Integer> stringWidthMap, String[] columns) {
        HashMap<Integer, Integer> widthMap = new HashMap<>();
        for (int i = 0; i < columns.length; i++){
            int width = stringWidthMap.get(columns[i]);
            widthMap.put(i, width);
        }
        return widthMap;
    }

    private List<String> getJoinColumns() {
        ArrayList<String> joinKeys = new ArrayList<>();
        for (int colIdx  = 0; colIdx < columns.length; colIdx++) {
            String column = columns[colIdx];
            SynopsisQuery.ColumnKey c = new SynopsisQuery.ColumnKey(table, column);
            if (globalColToJoinKey.containsKey(c)) {
                joinKeys.add(column);
                isJoinKey[colIdx] = true;
            }
        }
        return joinKeys;
    }

    private int getAttrSpecHashCode(int colidx, Map<SynopsisQuery.ColumnKey,String> globalColToJoinKey, boolean singleTable) {
        if (singleTable) {
            return String.valueOf(colidx).hashCode();
        }

        SynopsisQuery.ColumnKey c = new SynopsisQuery.ColumnKey(table, columns[colidx]);
        int attrSpecSeed;
        if (isJoinKey[colidx] && globalColToJoinKey.containsKey(c)) {
            attrSpecSeed = globalColToJoinKey.get(c).hashCode();
        } else {
            attrSpecSeed = columns[colidx].hashCode();
        }
        return attrSpecSeed;
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



    public int sketch_idx_first(int attr) {
        if (attr<0 || attr >= numAttributes) {
            throw new IllegalArgumentException("Out of range");
        }
        if (attr == 0) {
            return 0;
        }
        return attr - 1;
    }

    public void computeHashes(int att, int attrValue, int[] hashes, int[] seeds) {
        for (int i = 0; i < depth; i++) {
            hashes[i] = mix(attrValue, seeds[i], widthMap.get(att));
        }
    }

    public void computeHashes(int att, String attrValue, int[] hashes, int[] seeds) {
        for (int i = 0; i < depth; i++) {
            if (attrValue == null) {
                hashes[i] = -1;
            } else {
                hashes[i] = mix(attrValue.hashCode(), seeds[i], widthMap.get(att));
            }
        }
    }

    public void addIntArr(int[] record) {
        N += 1;
        for (int att = 0; att < numAttributes; att++) {
            int[] h = hashes[att];
            int[] s = seeds[att];
            int v = record[att];
            computeHashes(att, v, h, s);
        }
        addToSketch(null);
    }

    public int getTotalJoinAssignments() {
        double tot = 1.0;
        for (int joinKeyW : joinKeyWs) {
            tot *= joinKeyW;
        }
        return (int) tot;
    }

    public int[] getJoinKeyWs() {
        return joinKeyWs;
    }

    public void printParams() {
        System.out.println("ChowMin with d: "+ depth + ", width: " + widthMap.get(0));
    }

    static class HeavyHitter{
        String value;
        int[] hashes;
        double count = 0;
        public HeavyHitter(String value,
                           int[] hashes,
                           double count) {
            this.value = value;
            this.hashes = hashes;
            this.count = count;
        }
        public void incrCount() {
            count+=1;
        }

    }

    public void addStringArr(String[] record) {
        if (sepHHs)
            addStringArrSepHHs(record);
        else
            addStringArrNotSepHHs(record);

    }

    public void addStringArrNotSepHHs(String[] record) {
        N += 1;
        for (int att = 0; att < numAttributes; att++) {
            int[] h = hashes[att];
            int[] s = seeds[att];
            String v = record[att];
            computeHashes(att, v, h, s);
        }
        addToSketch(null);
    }

    public void addStringArrSepHHs(String[] record) {
        N += 1;
        boolean[] isHeavy = new boolean[numAttributes];

        for (int att = 0; att < numAttributes; att++) {
            int[] h = hashes[att];
            int[] s = seeds[att];
            String v = record[att];
            computeHashes(att, v, h, s);
            if (hhValues==null) {
                continue;
            }
            if (!isJoinKey[att]) {
                continue;
            }
            List<String> hhs = hhValues.getOrDefault(columns[att],null);
            if (hhs==null) {
                continue;
            }
            for (String hh: hhs) {
                if (hh.equals(v)) {
                    int[] hashesAtt = hashes[att].clone();
                    SynopsisQuery.ColumnKey c = new SynopsisQuery.ColumnKey(table, columns[att]);
                    heavyHitters
                            .computeIfAbsent(globalColToJoinKey.get(c), k -> new ArrayList<HeavyHitter>());

                    List<HeavyHitter> hhList = heavyHitters.get(globalColToJoinKey.get(c));
                    boolean hhExistsalready=false;
                    for (HeavyHitter hhObject : hhList) {
                        if (hhObject.value.equals(v)) {
                            hhObject.incrCount();
                            hhExistsalready=true;
                            break;
                        }
                    }
                    if (!hhExistsalready) {
                        hhList.add(new HeavyHitter(v, hashesAtt, 1.0));
                    }
                    isHeavy[att]=true;
                    break;
                }
            }
        }
        addToSketch(isHeavy);
    }


    private void addToSketch(boolean[] isHeavy) {
        for (int a1 = 0; a1 < numAttributes-1; a1++) { // summary for every pair in upper triangle
            for (int a2 = a1 + 1; a2 < numAttributes; a2++) {
                if (isHeavy!= null && (isHeavy[a1] || isHeavy[a2])) {
                    continue;
                }
                int[] ha1 = hashes[a1];
                int[] ha2 = hashes[a2];
                int sid = sketch_idx(a1, a2);
                for (int j = 0; j < depth; j++) {
                    if (ha1[j] == -1 || ha2[j] == -1) {
                        continue; // ignore null attribute values. Future version: don't make pairwise sketches with attributes that are all null.
                    }
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

    public int query(String[] record) {
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

    private void checkActive(ArrayList<Integer> active, String[] record) {
        for (int i = 0; i < record.length; i++) {
            if (record[i].equals("-1")) {
                active.add(i);
            }
        }
    }

    private HashSet<Integer> getAttrsInPath(ArrayList<Integer> active) {
        HashSet<Integer> attrs= new HashSet<Integer>(active.size());
        attrs.addAll(active);
        return attrs;
    }
    private int marginal(Integer attr, String predicate) {
        return sketches[sketch_idx_first(attr)].queryMarginal(predicate, attr);
    }
    private int marginal(Integer attr, int predicate) {
        return sketches[sketch_idx_first(attr)].queryMarginal(predicate, attr);
    }

    ArrayList<AttributeDimension> attributeDimensions = new ArrayList<>();
    private int queryFactors(List<Edge> f, int[] record, HashSet<Integer> attrsInPath) {
        constructDimensions(record, attrsInPath);
        int numDimensions = attributeDimensions.size();
        int[] indices = new int[numDimensions];
        return pathQuery(numDimensions, indices, f);
    }
    private int queryFactors(List<Edge> f, String[] record, HashSet<Integer> attrsInPath) {
        constructDimensions(record, attrsInPath);
        int numDimensions = attributeDimensions.size();
        int[] indices = new int[numDimensions];
        return pathQuery(numDimensions, indices, f);
    }

    private int pathQuery(int numDimensions, int[] indices, List<Edge> f) {
        double sum = 0;
        int[][] k = new int[depth][numAttributes];
        while (true) {
            for (int i = 0; i < numDimensions; i++) {
                AttributeDimension dim = attributeDimensions.get(i);
                int codomainIndex = indices[i];
                for (int j = 0; j < depth; j++) {
                    k[j][dim.attribute] = dim.valuesPerCodomain[j][codomainIndex];
                }
            }

            double product_p = 0;
            for (Edge edge : f) {
                int s = edge.getS();
                int t = edge.getT();
                int mine = Math.min(s,t);
                int maxe = Math.max(s,t);
                if (s == t) {
                    product_p = sketches[sketch_idx_first(s)].queryMarginalCodomainWOLatent(s, k);

                } else {
                    double jointFreq = sketches[sketch_idx(mine, maxe)].queryCodomainWOLatent(k,mine, maxe);
                    double denominator = sketches[sketch_idx_first(s)].queryMarginalCodomainWOLatent(s, k);
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
        return (int) (sum);
    }

    ArrayList<AttributeDimensionOneRep> attributeDimensionsOneRep = new ArrayList<>();

    // Test marginals
    public boolean testMarginals(int j, int s, int w) {
        List<Integer> sketchesWithS = new ArrayList<>();
        for (int i = 0; i < sketches.length; i++) {
            TWODCM sketch = sketches[i];
            if (sketch.attrS == s || sketch.attrT == s) {
                sketchesWithS.add(i);
            }
        }
        int[] margs = new int[sketchesWithS.size()];
        for (int i = 0; i < margs.length; i++) {
            int est = sketches[sketchesWithS.get(i)].marginalOneRep(j,s,w);
            margs[i] = est;
            if (i > 1 && margs[i] != margs[i - 1]) {
                throw new RuntimeException("Margs not equal");
            }
        }
        // check ig all margs are same;
        return true;
    }
    private double[] pathQueryOneRep(int j, int numDimensions, int[] indices, List<Edge> f, boolean isPrimaryKeyTable) {
        double sum = 0;
        int[] k = new int[numAttributes];
        double pkMarginal;
        while (true) {
            for (int i = 0; i < numDimensions; i++) {
                AttributeDimensionOneRep dim = attributeDimensionsOneRep.get(i);
                k[dim.attribute] = dim.valuesPerCodomain;
            }

            double product_p = 0;
            for (Edge edge : f) {
                int s = edge.getS();
                int t = edge.getT();
                int mine = Math.min(s,t);
                int maxe = Math.max(s,t);
                if (s == t) {
                    product_p = sketches[sketch_idx_first(s)].marginalOneRep(j, s, k[s]);
                } else {
                    double jointFreq = sketches[sketch_idx(mine, maxe)].queryCodomainWOLatentOneRep(j, k[mine], k[maxe]);
                    double denominator = sketches[sketch_idx_first(s)].marginalOneRep(j, s, k[s]);
                    if (jointFreq > denominator) {
                        throw new RuntimeException("Joint larger than denominator");
                    }
                    product_p *= jointFreq / denominator;
                }
            }
            if (product_p > 0) {
                sum += product_p;
            }

            int pos = numDimensions - 1;
            while (pos >= 0) {
                indices[pos]++;
                if (indices[pos] < attributeDimensionsOneRep.get(pos).codomainSize) {
                    break;
                }
                indices[pos] = 0;
                pos--;
            }

            if (pos < 0) {
                break; // all combinations exhausted
            }
        }
        if (isPrimaryKeyTable) { // pass on ratio of PK table.
            if (!columns[0].equals("Id") && !columns[0].equals("id")) {
                throw new RuntimeException("Columns[0] is " + columns[0]);
            }
            pkMarginal = sketches[sketch_idx_first(0)].marginalOneRep(j, 0, k[0]);
            double[] res = new double[2];
            res[0] = sum;
            res[1] = sum / Math.max(1.0, pkMarginal);
            return res;
        } else {
            return new double[]{sum};
        }
    }

    public double[][] estimateMIs() {
//        double[] MIs = new HashMap<>();
        double[][] MIs = new double[numAttributes*(numAttributes-1)/2][2+this.depth];
        int maxWidth = 0;
        for (int w : widthMap.values()) {
            if (w > maxWidth) {
                maxWidth = w;
            }
        }
        double[] marg1 = new double[maxWidth];
        double[] marg2 = new double[maxWidth];
        for (int a1 = 0; a1 < numAttributes-1; a1++) {
            for (int a2 = a1+1; a2 < numAttributes; a2++) {
                int sketch_idx = sketch_idx(a1, a2);
                double[] MIsList = new double[this.depth];
                for (int j = 0; j < depth; j++) {
                    double mi = estMI(widthMap.get(a1), widthMap.get(a2),a1, a2, sketch_idx, j, marg1, marg2);
                    MIsList[j] = mi;
                }
                Arrays.sort(MIsList);
                double medianMI = MIsList[MIsList.length/2];
                MIs[sketch_idx] = new double[]{a1,a2,medianMI};
            }
        }
        return MIs;
    }

    private double estMI(int width1, int width2, int a1, int a2, int sketch_idx, int j, double[] marg1, double[] marg2) {
        for (int w=0; w<width1; w++) {
            marg1[w] = sketches[sketch_idx_first(a1)]
                    .queryMarginalCodomain(a1, w, j);
        }
        for (int w = 0; w < width2; w++) {
            marg2[w] = sketches[sketch_idx_first(a2)]
                    .queryMarginalCodomain(a2, w, j);
        }

        double mi = 0;
        for (int w1 = 0; w1 < width1; w1++) {
            for (int w2 = 0; w2 < width2; w2++) {
                double joint = this.sketches[sketch_idx].jointCodomain(w1, w2,j);
                if (joint == 0) {
                    continue;
                }
                double mi_iter = joint/N * Math.log(joint/ (marg1[w1] * marg2[w2]));
                if (!Double.isNaN(mi_iter)) {
                    mi += mi_iter;
                }
            }
        }
        return mi;
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
            int[][] valuesCodomain = new int[depth][1];
            for (int j = 0; j < codomain.length; j++) {
                valuesCodomain[j] = new int[]{codomain[j]};
            }
            attributeDimensions.add(new AttributeDimension(i, valuesCodomain));
        }
    }

    void constructDimensions(String[] record, HashSet<Integer> attrsInPath) {
        attributeDimensions = new ArrayList<>();
        for (int i = 0; i < numAttributes; i++) {
            boolean isInPath = attrsInPath.contains(i);
            if (!isInPath) {
                continue;
            }
            int[] codomain;
            codomain = getCodomain(i, record[i]); // format int[d rows]
            int[][] valuesCodomain = new int[depth][1];
            for (int j = 0; j < codomain.length; j++) {
                valuesCodomain[j] = new int[]{codomain[j]};
            }
            attributeDimensions.add(new AttributeDimension(i, valuesCodomain));
        }
    }

    private int[] getCodomain(int attr, int predicate) {
        return sketches[sketch_idx_first(attr)].getCodomain(attr, predicate);
    }
    private int[] getCodomain(int attr, String predicate) {
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
//                    factors.add(new Edge(Math.min(v,u), Math.max(v,u)));
                    factors.add(new Edge(u, v));
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
        return depth;
    }

    @Override
    public int getw() {
        return 0;
    }

    public int getw(int att) {
        return widthMap.get(att);
    }
    public int getPKw() {
        return widthMap.get(0);
    }
    public void setJoinKeyw() {
        joinKeyWs = new int[joinColumns.size()];
        int i = 0;
        for (String joinColumn : joinColumns) {
            joinKeyWs[i] = getWidth(joinColumn);
            i++;
        }
    }

    private int getWidth(String col) {
        for (int i = 0; i <columns.length; i++) {
            if (columns[i].equals(col)) {
                return widthMap.get(i);
            }
        }
        throw new RuntimeException("Column "+ col+" not found in " + Arrays.toString(columns));
    }
    public int getN() {return N;}
    public int getNumColumns() {return numAttributes;};

    public void setMIs(double[][] MIs, boolean estMIs) {
        this.MIs = MIs;
        if (estMIs) {
            this.setting = "ChowM2DAllPairs_estMIs";
        } else {
            this.setting = "ChowM2DAllPairs";
        }
    }

    public record factorization(String[] record, List<Edge> factors, HashSet<Integer> active) {

    }
    public factorization factorize(List<EqualsTo> selectionPredicatesPerTable, List<SynopsisQuery.ColumnKey> unqColumns) {
        // for repetition j, apply selectionpredicates and return value for unqColumn at codomain val b
        // value for every attribute.
        // null = latent / not queried
        String[] record = new String[numAttributes];
        Arrays.fill(record, null);
        HashSet<Integer> active = new HashSet<>();

        // local predicates
        for (EqualsTo eq : selectionPredicatesPerTable) {

            String col = ((Column) eq.getLeftExpression()).getColumnName();
            String value = literalToString(eq.getRightExpression());

            int attr = getIdCol(col);
            if (attr < 0)
                continue;

            record[attr] = value;
            active.add(attr);
        }
        int joinAttrRoot = -1;

        // determine join attribute
        for (SynopsisQuery.ColumnKey unqColumn : unqColumns) {
            int attr = getIdCol(unqColumn.column());
            if (!active.contains(attr)) {
                if (joinAttrRoot == -1) {
                    joinAttrRoot = attr;
                }
                active.add(attr);
            }
        }

        if (joinAttrRoot ==-1)
            throw new RuntimeException("No join attribute found.");

        List<Edge> factors =
                buildFactorization(active, joinAttrRoot, numAttributes, MIs);
        return new factorization(record, factors, active);
    }

    private static String literalToString(Expression expr) {
        if (expr instanceof StringValue sv) {
            return sv.getValue();
        }
        if (expr instanceof LongValue lv) {
            return Long.toString(lv.getValue());
        }
        if (expr instanceof DoubleValue dv) {
            return Double.toString(dv.getValue());
        }
        return expr.toString();
    }

    public double[] queryFactorsJoinBucket(int j,
                                          List<Edge> factors,
                                          String[] record,
                                          HashMap<Integer, Integer> bucketPerAttribute,
                                          HashSet<Integer> active, boolean isPrimaryKeyTable) {
        constructDimensionsOneRep(record, bucketPerAttribute, j, active);

        int dims = attributeDimensionsOneRep.size();
        int[] indices = new int[dims];

        return pathQueryOneRep(j, dims, indices, factors, isPrimaryKeyTable);
    }

    private void constructDimensionsOneRep(String[] record,
                                           HashMap<Integer, Integer> bucketPerAttribute, int j,
                                           HashSet<Integer> active) {
        attributeDimensionsOneRep = new ArrayList<>();

        for (int attr : active) {
            int valuesCodomain;

            if (bucketPerAttribute.containsKey(attr)) {
                // Join key fixed to bucket b
                valuesCodomain = bucketPerAttribute.get(attr);
            }
            else if (record[attr] != null) {
                // Predicate attribute
                int[] codomain = getCodomain(attr, record[attr]);
                valuesCodomain = codomain[j];
            }
            else {
                throw new RuntimeException("Inactive attribute");
            }
            attributeDimensionsOneRep.add(
                    new AttributeDimensionOneRep(attr, valuesCodomain)
            );
        }
    }


    int getIdCol(String col) {
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].equals(col)) {
                return i;
            }
        }
        throw new RuntimeException("Column " + col + " not found for table: " + table);
    }
}
