package org;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Random;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;

public class CountMin {
    private final int[][] CM;
    private final int depth;
    private final int width;
    HashFunction[] xx;
    int[] reusableHashes;

    public CountMin(int depth, int width) {
        this.depth = depth;
        this.width = width;
        CM = new int[depth][width];
        for (int i = 0; i < depth; i++) {
            for (int j = 0; j < width; j++) {
                CM[i][j] = 0;
            }
        }
        xx = new HashFunction[depth];
        for (int i = 0; i < depth; i++) {
            xx[i] = Hashing.murmur3_32_fixed(i + 1);
        }
        reusableHashes = new int[depth];
    }

    public void add(int attrValue) {

        computeHashes(xx, attrValue, depth, width, reusableHashes);
        for (int j = 0; j < depth; j++) {
            CM[j][reusableHashes[j]] += 1;
        }
    }
    public void add(int e1, int e2) {
        long compositeHash = ((long) e1 << 32) | (e2 & 0xFFFFFFFFL);
        computeHashesLong(xx, compositeHash, depth, width, reusableHashes);
        for (int j = 0; j < depth; j++) {
            CM[j][reusableHashes[j]] += 1;
        }
    }

    public int query(int attrValue) {
        computeHashes(xx, attrValue, depth, width, reusableHashes);
        int minEstimate = Integer.MAX_VALUE;
        for (int i = 0; i < depth; i++) {
            if (CM[i][reusableHashes[i]] < minEstimate) {
                minEstimate = CM[i][reusableHashes[i]];
            }
        }
        return minEstimate;
    }
    public int query(int e1, int e2) {
        long compositeHash = ((long) e1 << 32) | (e2 & 0xFFFFFFFFL);
        computeHashesLong(xx, compositeHash, depth, width, reusableHashes);
        int minEstimate = Integer.MAX_VALUE;
        for (int i = 0; i < depth; i++) {
            if (CM[i][reusableHashes[i]] < minEstimate) {
                minEstimate = CM[i][reusableHashes[i]];
            }
        }
        return minEstimate;
    }

    public double queryCell(int row, int column) {
        return CM[row][column];
    }


    public static void computeHashes(HashFunction[] hashFunctions, int attrValue, int depth, int width, int[] hashes) {
        for (int i = 0; i < depth; i++) {
            int hash = hashFunctions[i].hashInt(attrValue).asInt();
            hashes[i] = fastPositiveModulo(hash, width);
        }
    }
    public static void computeHashesLong(HashFunction[] hashFunctions, long attrValue, int depth, int width, int[] hashes) {
        for (int i = 0; i < depth; i++) {
            int hash = hashFunctions[i].hashLong(attrValue).asInt();
            hashes[i] = fastPositiveModulo(hash, width);
        }
    }
    // Fast modulo for positive results
    private static int fastPositiveModulo(int x, int mod) {
        int r = x & (mod - 1); // works if mod is a power of two
        return mod == (mod & -mod) ? r : (x % mod + mod) % mod;
    }

    public int memory() {
        return depth  * width * 4;
    }

}
