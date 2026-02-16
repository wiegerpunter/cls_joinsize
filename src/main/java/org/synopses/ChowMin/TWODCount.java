package org.synopses.ChowMin;

import com.google.common.hash.HashFunction;
import org.utils.Edge;

import java.util.Arrays;
import java.util.stream.IntStream;

public class TWODCount {
//    public int[][][] CM;
    public int[] CMR;
    private final int depth;
    private final int width;
    int attrS; int attrT;

    int[] reusableHashes;
    int[] signHashes;
    int[] seeds;
    int[] seedsG;


    public TWODCount(int depth, int width, Edge edge) {
        this.depth = depth;
        this.width = width;
        this.attrS = edge.getS();
        this.attrT = edge.getT();
        init();

    }

    public TWODCount(int depth, int width, int s, int t) {
        this.depth = depth;
        this.width = width;
        this.attrS = s;
        this.attrT = t;
        init();
    }

    private void init() {
        seeds = new int[depth];
        seedsG = new int[depth];

        for (int j = 0; j < seeds.length; j++) {
            seeds[j] = j + attrS + 20*attrT;
            seedsG[j] = j + attrT + 30*attrS;
        }

        CMR = new int[depth * width * width];
        reusableHashes = new int[depth];
        signHashes = new int[depth];
    }

    public void add(int e1, int e2) {
        computeHashes(e1, e2, reusableHashes, signHashes);

        for (int j = 0; j < depth; j++) {
            CMR[idx(j, reusableHashes[j])] += signHashes[j];
        }
    }

    public int query(int e1, int e2) {
        computeHashes(e1, e2, reusableHashes, signHashes);

        int[] ests= new int[depth];
        for (int i = 0; i < depth; i++) {
            ests[i] = CMR[idx(i, reusableHashes[i])]*signHashes[i];
        }
        Arrays.sort(ests);
        return ests[depth/2];
    }

    static int mix(int x, int y, int seed) {
        long z = key(x, y);
        z ^= seed;
        z *= 0x85ebca6bL;
        z ^= z >>> 13;
        z *= 0xc2b2ae35L;
        z ^= z >>> 16;
        return (int) z;
    }
    static int signHash(int x, int y, int seed) {
        long z = key(x, y);
        z ^= seed;
        z *= 0xcc9e2d51L;
        z ^= z >>> 13;
        z *= 0x1b873593L;
        z ^= z >>> 16;
        return (z < 0) ? -1 : +1;
    }

    static long key(int x, int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }

    public void computeHashes(int attr1, int attr2, int[] hashes, int[] signHashes) {
        final int mask = width*width - 1;
        for (int i = 0; i < depth; i++) {
            hashes[i] = mix(attr1, attr2, seeds[i]) & mask;
            signHashes[i] = signHash(attr1, attr2, seedsG[i]);
        }

    }

    public int memory() {
        return depth  * width * width * 4;
    }

    public int idx(int d, int x) {
        return d * (width * width) + x;
    }

}
