package org;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

public class CountMinHashColumns {
    public int[][][] CM;
    private final int depth;
    private final int width;
    HashFunction[] xxA;
    HashFunction[] xxB;

    int[] reusableHashesA;
    int[] reusableHashesB;


    public CountMinHashColumns(int depth, int width) {
        this.depth = depth;
        this.width = width;
        CM = new int[depth][width][width];
        for (int i = 0; i < depth; i++) {
            for (int j = 0; j < width; j++) {
                for  (int k = 0; k < width; k++) {
                    CM[i][j][k] =0;
                }
            }
        }
        xxA = new HashFunction[depth];
        xxB = new HashFunction[depth];

        for (int i = 0; i < depth; i++) {
            xxA[i] = Hashing.murmur3_32_fixed(i + 1);
            xxB[i] = Hashing.murmur3_32_fixed(i + 10);

        }
        reusableHashesA = new int[depth];
        reusableHashesB = new int[depth];
    }

    public void add(int e1, int e2) {
        computeHashes(xxA, e1, depth, width, reusableHashesA);
        computeHashes(xxB, e2, depth, width, reusableHashesB);

        for (int j = 0; j < depth; j++) {
            CM[j][reusableHashesA[j]][reusableHashesB[j]] += 1;
        }
    }

    public int query(int e1, int e2) {
        computeHashes(xxA, e1, depth, width, reusableHashesA);
        computeHashes(xxB, e2, depth, width, reusableHashesB);
        int minEstimate = Integer.MAX_VALUE;
        for (int i = 0; i < depth; i++) {
            if (CM[i][reusableHashesA[i]][reusableHashesB[i]] < minEstimate) {
                minEstimate = CM[i][reusableHashesA[i]][reusableHashesB[i]];
            }
        }
        return minEstimate;
    }


    public static void computeHashes(HashFunction[] hashFunctions, int attrValue, int depth, int width, int[] hashes) {
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

    public double queryCellL(int attr, int i1) {
        computeHashes(xxB, i1, depth, width, reusableHashesB);
        int minEstimate = Integer.MAX_VALUE;
        for (int i = 0; i < depth; i++) {
            if (CM[i][attr][reusableHashesB[i]] < minEstimate) {
                minEstimate = CM[i][attr][reusableHashesB[i]];
            }
        }
        return minEstimate;
    }
    public double queryCellR(int i1, int attr) {
        computeHashes(xxA, i1, depth, width, reusableHashesA);
        int minEstimate = Integer.MAX_VALUE;
        for (int i = 0; i < depth; i++) {
            if (CM[i][reusableHashesA[i]][attr] < minEstimate) {
                minEstimate = CM[i][reusableHashesA[i]][attr];
            }
        }
        return minEstimate;
    }
}
