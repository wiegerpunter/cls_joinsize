package org.synopses.ChowMin;

import com.google.common.hash.HashFunction;
import org.utils.Edge;

import java.util.stream.IntStream;

public class TWODCM {
//    public int[][][] CM;
    public int[] CMR;
    private final int depth;
    private final int width;
    int attrS; int attrT;
    HashFunction[] xxS;
    HashFunction[] xxT;

    int[] reusableHashesS;
    int[] reusableHashesT;
    int[] seedsS;
    int[] seedsT;

    public TWODCM(int depth, int width, Edge edge) {
        this.depth = depth;
        this.width = width;
        this.attrS = edge.getS();
        this.attrT = edge.getT();

        seedsS = new int[depth];
        seedsT = new int[depth];

        for (int j = 0; j < seedsS.length; j++) {
            seedsS[j] = j + attrS; // todo: make consistent with attr.hashCode() if ever using this
            seedsT[j] = j + attrT;
        }
        CMR = new int[depth * width * width];
        reusableHashesS = new int[depth];
        reusableHashesT = new int[depth];
    }
    public TWODCM(int depth, int width, int s, int t, String sName, String tName) {
        this.depth = depth;
        this.width = width;
        this.attrS = s;
        this.attrT = t;

        seedsS = new int[depth];
        seedsT = new int[depth];

        for (int j = 0; j < seedsS.length; j++) {
            seedsS[j] = j + sName.hashCode(); // todo: make consistent with attr.hashCode()
            seedsT[j] = j + tName.hashCode();
        }
        CMR = new int[depth * width * width];
        reusableHashesS = new int[depth];
        reusableHashesT = new int[depth];
    }

    public void add(int e1, int e2) {
//        computeHashes(xxS, e1, reusableHashesS);
//        computeHashes(xxT, e2, reusableHashesT);
        computeHashes(e1, reusableHashesS, true);
        computeHashes(e2, reusableHashesT, false);

        for (int j = 0; j < depth; j++) {
//            CM[j][reusableHashesS[j]][reusableHashesT[j]] += 1;
            CMR[idx(j, reusableHashesS[j], reusableHashesT[j])] += 1;
        }
    }


    public void addHashed(int[] hashS, int[] hashT) {
        for (int j = 0; j < depth; j++) {
//            CM[j][reusableHashesS[j]][reusableHashesT[j]] += 1;
            CMR[idx(j, hashS[j], hashT[j])] += 1;
        }
    }


    public int query(int e1, int e2) {
//        computeHashes(xxS, e1, reusableHashesS);
//        computeHashes(xxT, e2, reusableHashesT);

        computeHashes(e1, reusableHashesS, true);
        computeHashes(e2, reusableHashesT, false);
        int minEstimate = Integer.MAX_VALUE;
        for (int i = 0; i < depth; i++) {
//            if (CM[i][reusableHashesS[i]][reusableHashesT[i]] < minEstimate) {
//                minEstimate = CM[i][reusableHashesS[i]][reusableHashesT[i]];
//            }
            if (CMR[idx(i, reusableHashesS[i], reusableHashesT[i])] < minEstimate) {
                minEstimate = CMR[idx(i, reusableHashesS[i], reusableHashesT[i])];
            }
        }
        return minEstimate;
    }

    static int mix(int x, int seed, int mask) {
        x ^= seed;
        x *= 0x85ebca6b;
        x ^= x >>> 13;
        x *= 0xc2b2ae35;
        x = x ^ (x >>> 16);
        x= x % mask;
        if (x<0) {
            return x+mask;
        } else {
            return x;
        }
    }


    public void computeHashes(int attrValue, int[] hashes, boolean source) {
//        final int mask = width - 1;
        if (source) { // never true
            for (int i = 0; i < depth; i++) {
                hashes[i] = mix(attrValue, seedsS[i], width);
            }
        } else {
            for (int i = 0; i < depth; i++) {
                hashes[i] = mix(attrValue, seedsT[i], width);
            }
        }
    }

    public void computeHashes(String attrValue, int[] hashes, boolean source) {
//        final int mask = width - 1;
        if (source) { // never true
            for (int i = 0; i < depth; i++) {
                hashes[i] = mix(attrValue.hashCode(), seedsS[i], width);
            }
        } else {
            for (int i = 0; i < depth; i++) {
                hashes[i] = mix(attrValue.hashCode(), seedsT[i], width);
            }
        }
    }

    static public int hash(int attrValue, int attribute, int row, int width) {
        int seed = row + attribute;
        return mix(attrValue, seed, width);
    }

    public int memory() {
        return depth  * width * width * 4;
    }


    public int queryCodomain(int[][] k, Edge edge, boolean hasLatent, int row) {
        int minEstimate = Integer.MAX_VALUE;
        if (hasLatent) {
            return CMR[idx(row, k[row][edge.getS()], k[row][edge.getT()])];
//            return CM[row][k[row][edge.getS()]][k[row][edge.getT()]];
        } else {
            for (int j = 0; j < k.length; j++) {
//                int estimate = CM[j][k[j][edge.getS()]][k[j][edge.getT()]];
                int estimate = CMR[idx(j, k[j][edge.getS()], k[j][edge.getT()])];
                if (minEstimate > estimate) {
                    minEstimate = estimate;
                }
            }
        }
        return minEstimate;
    }

    public int queryCodomainWOLatent(int[][] k, Edge edge) {
        int minEstimate = Integer.MAX_VALUE;
        for (int j = 0; j < k.length; j++) {
//                int estimate = CM[j][k[j][edge.getS()]][k[j][edge.getT()]];
                int estimate = CMR[idx(j, k[j][edge.getS()], k[j][edge.getT()])]; // TODO: how can 16 be 0??
                if (minEstimate > estimate) {
                    minEstimate = estimate;
                }
        }
        return minEstimate;
    }

    public int jointCodomain(int w1, int w2, int j) {
        return CMR[idx(j, w1, w2)];

    }

    public int[] getCodomain(int attr, int predicate) {
        if (attr == attrS) {
//            computeHashes(xxS, predicate, reusableHashesS);
            computeHashes(predicate, reusableHashesS, true);

            return reusableHashesS;
        } else {
            computeHashes(predicate, reusableHashesT, false);

            return reusableHashesT;
        }

    }
    public int[] getCodomain(int attr, String predicate) {
        if (attr == attrS) {
//            computeHashes(xxS, predicate, reusableHashesS);
            computeHashes(predicate, reusableHashesS, true);

            return reusableHashesS;
        } else {
            computeHashes(predicate, reusableHashesT, false);

            return reusableHashesT;
        }

    }

    public int queryMarginalCodomain(int condAttr, int[][] k, boolean hasLatent, int row) {
        int[] predIdxs;
        int[] rows;
        if (hasLatent) {
            predIdxs = new int[1];
            predIdxs[0] = k[row][condAttr];
            rows = new int[]{row};
        } else {
            predIdxs = new int[k.length];
            rows = new int[k.length];
            for (int j = 0; j < predIdxs.length; j++) {
                predIdxs[j] = k[j][condAttr];
                rows[j] = j;
            }
        }

        return marginal(predIdxs, condAttr, rows);
    }

    public int queryMarginalCodomainWOLatent(int condAttr, int[][] k) {
        int[] predIdxs;
        int[] rows;
        predIdxs = new int[k.length];
        rows = new int[k.length];
        for (int j = 0; j < predIdxs.length; j++) {
            predIdxs[j] = k[j][condAttr];
            rows[j] = j;
        }

        return marginal(predIdxs, condAttr, rows);
    }

    public int queryMarginalCodomain(int condAttr, int w1, int j) {
        int minEstimate = Integer.MAX_VALUE;
        int sumRowEst =0;
        if (attrS==condAttr) {
            for (int i = 0; i < width; i++) {
                sumRowEst+= CMR[idx(j, w1, i)];
            }
        } else {
            for (int i = 0; i<width; i++){
                sumRowEst+= CMR[idx(j, i, w1)];
            }
        }
        if (sumRowEst < minEstimate) {
            minEstimate = sumRowEst;
        }
        return minEstimate;
    }

    public int queryMarginal(int predicate, int attr) {
        int[] predIdxs = getPredIdx(predicate, attr);
        int[] rows = IntStream.range(0, predIdxs.length).toArray();
        return marginal(predIdxs, attr, rows);
    }
    public int queryMarginal(String predicate, int attr) {
        int[] predIdxs = getPredIdx(predicate, attr);
        int[] rows = IntStream.range(0, predIdxs.length).toArray();
        return marginal(predIdxs, attr, rows);
    }

    int marginal(int[] predIdxs, int attr, int[] rows) {
        int minEstimate = Integer.MAX_VALUE;
        for (int j = 0; j < predIdxs.length; j++) {
            int sumRowEst=0;
            int predIdx = predIdxs[j];
            if (attrS == attr) {
                for (int i = 0; i < width; i++) {
//                    sumRowEst += CM[rows[j]][predIdx][i];
                    sumRowEst+= CMR[idx(rows[j], predIdx, i)];
                }
            } else {
                for (int i = 0; i < width; i++) {
//                    sumRowEst += CM[rows[j]][i][predIdx];
                    sumRowEst += CMR[idx(rows[j], i, predIdx)];
                }
            }
            if (minEstimate > sumRowEst) {
                minEstimate = sumRowEst;
            }
        }
        return minEstimate;
    }

    int[] getPredIdx(int predicate, int attr) {
        if (attrS == attr) {
//            computeHashes(xxS, predicate, reusableHashesS);
            computeHashes(predicate, reusableHashesS, true);

            return reusableHashesS;
        } else {
            computeHashes(predicate, reusableHashesT, false);

//            computeHashes(xxT, predicate, reusableHashesT);
            return reusableHashesT;
        }
    }

    int[] getPredIdx(String predicate, int attr) {
        if (attrS == attr) {
//            computeHashes(xxS, predicate, reusableHashesS);
            computeHashes(predicate, reusableHashesS, true);

            return reusableHashesS;
        } else {
            computeHashes(predicate, reusableHashesT, false);

//            computeHashes(xxT, predicate, reusableHashesT);
            return reusableHashesT;
        }
    }

    public int idx(int d, int x, int y) {
        return d * (width * width) + x * (width) + y;
    }

    private int idx_copy(int d, int x, int y) {
        return d * (width * width) + x * (width) + y;
    }

    public int getCount(int row, int u, int v, int xu, int xv) {
        // must ensure orientation matches (u is row variable, v is column variable)

        if (this.attrS== u && this.attrT == v) {
            return CMR[idx(row, xu, xv)];
        } else if (this.attrS == v && this.attrT == u) {
            // transpose
            return CMR[idx(row, xv, xu)];
        } else {
            throw new RuntimeException("TWODCM does not match queried edge");
        }
    }
}
