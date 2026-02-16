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
            seedsS[j] = j + attrS;
            seedsT[j] = j + attrT;
        }


//        CM = new int[depth][width][width];
        CMR = new int[depth * width * width];
//        for (int i = 0; i < depth; i++) {
//            for (int j = 0; j < width; j++) {
//                for  (int k = 0; k < width; k++) {
//                    CM[i][j][k] =0;
//                }
//            }
//        }
//        xxS = new HashFunction[depth];
//        xxT = new HashFunction[depth];

//        for (int i = 0; i < depth; i++) {
//            xxS[i] = Hashing.murmur3_32_fixed(i + attrS);
//            xxT[i] = Hashing.murmur3_32_fixed(i + attrT);
//
//        }
        reusableHashesS = new int[depth];
        reusableHashesT = new int[depth];
    }
    public TWODCM(int depth, int width, int s, int t) {
        this.depth = depth;
        this.width = width;
        this.attrS = s;
        this.attrT = t;

        seedsS = new int[depth];
        seedsT = new int[depth];

        for (int j = 0; j < seedsS.length; j++) {
            seedsS[j] = j + attrS;
            seedsT[j] = j + attrT;
        }
        CMR = new int[depth * width * width];
        reusableHashesS = new int[depth];
        reusableHashesT = new int[depth];
    }

    public void add(int e1, int e2) {
//        computeHashes(xxS, e1, reusableHashesS);
//        computeHashes(xxT, e2, reusableHashesT);
        computeHashes(e1, reusableHashesS);
        computeHashes(e2, reusableHashesT);

        for (int j = 0; j < depth; j++) {
//            CM[j][reusableHashesS[j]][reusableHashesT[j]] += 1;
            CMR[idx(j, reusableHashesS[j], reusableHashesT[j])] += 1;
        }
    }

    public int query(int e1, int e2) {
//        computeHashes(xxS, e1, reusableHashesS);
//        computeHashes(xxT, e2, reusableHashesT);

        computeHashes(e1, reusableHashesS);
        computeHashes(e2, reusableHashesT);
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

    static int mix(int x, int seed) {
        x ^= seed;
        x *= 0x85ebca6b;
        x ^= x >>> 13;
        x *= 0xc2b2ae35;
        return x ^ (x >>> 16);
    }

    public void computeHashes(int attrValue, int[] hashes) {
        final int mask = width - 1;
        if (attrValue== attrS) {
            for (int i = 0; i < depth; i++) {
                hashes[i] = mix(attrValue, seedsS[i]) & mask;
            }
        } else {

            for (int i = 0; i < depth; i++) {
                hashes[i] = mix(attrValue, seedsT[i]) & mask;
            }
        }
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
                int estimate = CMR[idx(j, k[j][edge.getS()], k[j][edge.getT()])];
                if (minEstimate > estimate) {
                    minEstimate = estimate;
                }
        }
        return minEstimate;
    }

    public int[] getCodomain(int attr, int predicate) {
        if (attr == attrS) {
//            computeHashes(xxS, predicate, reusableHashesS);
            computeHashes(predicate, reusableHashesS);

            return reusableHashesS;
        } else {
            computeHashes(predicate, reusableHashesT);

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

    public int queryMarginal(int predicate, int attr) {
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
            computeHashes(predicate, reusableHashesS);

            return reusableHashesS;
        } else {
            computeHashes(predicate, reusableHashesT);

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

}
