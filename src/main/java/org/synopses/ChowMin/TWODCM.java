package org.synopses.ChowMin;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.utils.Edge;

public class TWODCM {
    public int[][][] CM;
    private final int depth;
    private final int width;
    int attrS; int attrT;
    HashFunction[] xxS;
    HashFunction[] xxT;

    int[] reusableHashesS;
    int[] reusableHashesT;


    public TWODCM(int depth, int width, Edge edge) {
        this.depth = depth;
        this.width = width;
        this.attrS = edge.getS();
        this.attrT = edge.getT();

        CM = new int[depth][width][width];
        for (int i = 0; i < depth; i++) {
            for (int j = 0; j < width; j++) {
                for  (int k = 0; k < width; k++) {
                    CM[i][j][k] =0;
                }
            }
        }
        xxS = new HashFunction[depth];
        xxT = new HashFunction[depth];

        for (int i = 0; i < depth; i++) {
            xxS[i] = Hashing.murmur3_32_fixed(i + attrS);
            xxT[i] = Hashing.murmur3_32_fixed(i + attrT);

        }
        reusableHashesS = new int[depth];
        reusableHashesT = new int[depth];
    }

    public void add(int e1, int e2) {
        computeHashes(xxS, e1, reusableHashesS);
        computeHashes(xxT, e2, reusableHashesT);

        for (int j = 0; j < depth; j++) {
            CM[j][reusableHashesS[j]][reusableHashesT[j]] += 1;
        }
    }

    public int query(int e1, int e2) {
        computeHashes(xxS, e1, reusableHashesS);
        computeHashes(xxT, e2, reusableHashesT);
        int minEstimate = Integer.MAX_VALUE;
        for (int i = 0; i < depth; i++) {
            if (CM[i][reusableHashesS[i]][reusableHashesT[i]] < minEstimate) {
                minEstimate = CM[i][reusableHashesS[i]][reusableHashesT[i]];
            }
        }
        return minEstimate;
    }

    public void computeHashes(HashFunction[] hashFunctions, int attrValue, int[] hashes) {
        for (int i = 0; i < depth; i++) {
            int hash = hashFunctions[i].hashInt(attrValue).asInt();
            hashes[i] = fastPositiveModulo(hash, width);
        }
    }
    // Fast modulo for positive results
    private static int fastPositiveModulo(int x, int mod) {
        int r = x & (mod - 1); // works if mod is a power of two
        return mod == (mod & -mod) ? r : (x % mod + mod) % mod;
    }

    public int memory() {
        return depth  * width * width * 4;
    }


    public Object queryCodomain(int[][] k, Edge edge) {
        int minEstimate = Integer.MAX_VALUE;
        for (int j = 0; j < k.length; j++) {
            int estimate = CM[j][k[j][edge.getS()]][k[j][edge.getT()]];
            if (minEstimate > estimate) {
                minEstimate = estimate;
            }
        }
        return minEstimate;
    }

    public int[] getCodomain(int attr, int predicate) {
        if (attr == attrS) {
            computeHashes(xxS, predicate, reusableHashesS);
            return reusableHashesS;
        } else {
            computeHashes(xxT, predicate, reusableHashesT);
            return reusableHashesT;
        }

    }

    public int queryMarginalCodomain(int condAttr, int[][] k) {
        int[] predIdxs = new int[depth];
        for (int j = 0; j < depth; j++) {
            predIdxs[j] = k[j][condAttr];
        }
        return marginal(predIdxs, condAttr);
    }

    public int queryMarginal(int predicate, int attr) {
        int[] predIdxs = getPredIdx(predicate, attr);
        return marginal(predIdxs, attr);
    }

    private int marginal(int[] predIdxs, int attr) {
        int minEstimate = Integer.MAX_VALUE;
        for (int j = 0; j < depth; j++) {
            int sumRowEst = 0;
            int predIdx = predIdxs[j];
            if (attrS == attr) {
                for (int i = 0; i < width; i++) {
                    sumRowEst += CM[j][predIdx][i];
                }
            } else {
                for (int i = 0; i < width; i++) {
                    sumRowEst += CM[j][i][predIdx];
                }
            }
            if (minEstimate > sumRowEst) {
                minEstimate = sumRowEst;
            }
        }
        return minEstimate;
    }

    private int[] getPredIdx(int predicate, int attr) {
        if (attrS == attr) {
            computeHashes(xxS, predicate, reusableHashesS);
            return reusableHashesS;
        } else {
            computeHashes(xxT, predicate, reusableHashesT);
            return reusableHashesT;
        }
    }
}
