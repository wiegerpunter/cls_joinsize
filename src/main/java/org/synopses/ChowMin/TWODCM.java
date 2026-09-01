package org.synopses.ChowMin;

import com.google.common.hash.HashFunction;
import org.utils.Edge;

public class TWODCM {
//    public int[][][] CM;
    public int[] CMR;
    private final int depth;
    private final int widthS;
    private final int widthT;

    int attrS; int attrT;

    int[] reusableHashesS;
    int[] reusableHashesT;
    int[] seedsS;
    int[] seedsT;

    public TWODCM(int depth, int width, Edge edge) {
        this.depth = depth;
        this.widthS = width;
        this.widthT = width;

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
        throw new RuntimeException("Outdated");
    }
    public TWODCM(int depth, int widthS, int widthT, int s, int t, int sHashCode, int tHashCode) {
        this.depth = depth;
        this.widthS = widthS;
        this.widthT = widthT;
        this.attrS = s;
        this.attrT = t;

        seedsS = new int[depth];
        seedsT = new int[depth];

        for (int j = 0; j < seedsS.length; j++) {
            seedsS[j] = (j+1)* sHashCode;
            seedsT[j] = (j+1)* tHashCode;
        }
        CMR = new int[depth * widthS * widthT];
        reusableHashesS = new int[depth];
        reusableHashesT = new int[depth];
    }

    public void add(int e1, int e2) {
        computeHashes(e1, reusableHashesS, true);
        computeHashes(e2, reusableHashesT, false);

        for (int j = 0; j < depth; j++) {
            CMR[idx(j, reusableHashesS[j], reusableHashesT[j])] += 1;
        }
    }


    public void addHashed(int[] hashS, int[] hashT) {
        for (int j = 0; j < depth; j++) {
            CMR[idx(j, hashS[j], hashT[j])] += 1;
        }
    }


    public int query(int e1, int e2) {
        computeHashes(e1, reusableHashesS, true);
        computeHashes(e2, reusableHashesT, false);
        int minEstimate = Integer.MAX_VALUE;
        for (int i = 0; i < depth; i++) {
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
                hashes[i] = mix(attrValue, seedsS[i], widthS);
            }
        } else {
            for (int i = 0; i < depth; i++) {
                hashes[i] = mix(attrValue, seedsT[i], widthT);
            }
        }
    }

    public void computeHashes(String attrValue, int[] hashes, boolean source) {
//        final int mask = width - 1;
        if (source) { // never true
            for (int i = 0; i < depth; i++) {
                hashes[i] = mix(attrValue.hashCode(), seedsS[i], widthS);
            }
        } else {
            for (int i = 0; i < depth; i++) {
                hashes[i] = mix(attrValue.hashCode(), seedsT[i], widthT);
            }
        }
    }

    static public int hash(int attrValue, int attribute, int row, int width) {
        int seed = row + attribute;
        return mix(attrValue, seed, width);
    }

    public int memory() {
        return depth  * widthS * widthT * 4;
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

    public int queryCodomainWOLatent(int[][] k, int s, int t) {
        int minEstimate = Integer.MAX_VALUE;
        for (int j = 0; j < k.length; j++) {
//                int estimate = CM[j][k[j][edge.getS()]][k[j][edge.getT()]];
                int estimate = queryCodomainWOLatentOneRep(j, k[j][s], k[j][t]);//CMR[idx(j, k[j][edge.getS()], k[j][edge.getT()])]; // TODO: how can 16 be 0??
                if (minEstimate > estimate) {
                    minEstimate = estimate;
                }
        }
        return minEstimate;
    }

    public int queryCodomainWOLatentOneRep(int j, int ks, int kt) {
        return CMR[idx(j, ks, kt)];
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
        if (hasLatent) {
            predIdxs = new int[1];
            predIdxs[0] = k[row][condAttr];
        } else {
            predIdxs = new int[k.length];
            for (int j = 0; j < predIdxs.length; j++) {
                predIdxs[j] = k[j][condAttr];
            }
        }

        return marginal(predIdxs, condAttr);
    }

    public int queryMarginalCodomainWOLatent(int condAttr, int[][] k) {
        int[] predIdxs;
        predIdxs = new int[k.length];
        for (int j = 0; j < predIdxs.length; j++) {
            predIdxs[j] = k[j][condAttr];
        }

        return marginal(predIdxs, condAttr);
    }

    public int queryMarginalCodomain(int condAttr, int w, int j) {
        int minEstimate = Integer.MAX_VALUE;
        int sumRowEst =0;
        if (attrS==condAttr) {
            for (int i = 0; i < widthT; i++) {
                sumRowEst+= CMR[idx(j, w, i)];
            }
        } else {
            for (int i = 0; i<widthS; i++){
                sumRowEst+= CMR[idx(j, i, w)];
            }
        }
        if (sumRowEst < minEstimate) {
            minEstimate = sumRowEst;
        }
        return minEstimate;
    }

    public int queryMarginal(int predicate, int attr) {
        int[] predIdxs = getPredIdx(predicate, attr);
        return marginal(predIdxs, attr);
    }
    public int queryMarginal(String predicate, int attr) {
        int[] predIdxs = getPredIdx(predicate, attr);
        return marginal(predIdxs, attr);
    }

    int marginal(int[] predIdxs, int attr) {
        int minEstimate = Integer.MAX_VALUE;
        for (int j = 0; j < predIdxs.length; j++) {
            int sumRowEst= marginalOneRep(j, attr, predIdxs[j]);
            if (minEstimate > sumRowEst) {
                minEstimate = sumRowEst;
            }
        }
        return minEstimate;
    }

    int marginalOneRep(int rep, int attr,int predIdx) {
        int sumRowEst=0;
        if (attrS == attr) {
            for (int i = 0; i < widthT; i++) {
                sumRowEst+= CMR[idx(rep, predIdx, i)];
            }
        } else {
            for (int i = 0; i < widthS; i++) {
                sumRowEst += CMR[idx(rep, i, predIdx)];
            }
        }
        return sumRowEst;
    }

    int[] getPredIdx(int predicate, int attr) {
        if (attrS == attr) {
            computeHashes(predicate, reusableHashesS, true);
            return reusableHashesS;
        } else {
            computeHashes(predicate, reusableHashesT, false);
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
        return d * (widthS * widthT) + x * (widthT) + y;
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
