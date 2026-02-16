package org.synopses;

import java.util.Arrays;

public class CountSketch {
    private final int[][] sketch;
    private final int depth;
    private final int width;
    private int[] seeds;
    private int[] seedsG;
    int[] reusableHashes;
    int[] reusableSignHashes;

    public CountSketch(int depth, int width, int attribute) {
        this.depth = depth;
        this.width = width;
        sketch = new int[depth][width];
        for (int i = 0; i < depth; i++) {
            for (int j = 0; j < width; j++) {
                sketch[i][j] = 0;
            }
        }
        seeds = new int[depth];
        seedsG = new int[depth];

        for (int j = 0; j < seeds.length; j++) {
            seeds[j] = j + attribute;
            seedsG[j] = 20*attribute + 30+j;
        }

        reusableHashes = new int[depth];
        reusableSignHashes = new int[depth];

    }

    public void add(int attrValue) {

        computeHashes(attrValue, reusableHashes, reusableSignHashes);
        for (int j = 0; j < depth; j++) {
            sketch[j][reusableHashes[j]] += reusableSignHashes[j];
        }
    }
    public void add(int e1, int e2) {
        throw new RuntimeException("should not be called now");
    }

    public int query(int attrValue) {
        computeHashes(attrValue,reusableHashes, reusableSignHashes);
        int[] ests = new int[depth];
        for (int i = 0; i < depth; i++) {
            ests[i] = sketch[i][reusableHashes[i]]*reusableSignHashes[i];
        }
        Arrays.sort(ests);
        return ests[depth/2];
    }
    public int query(int e1, int e2) {
        throw new RuntimeException("Shou;d not be called now");
    }

    public void computeHashes(int attrValue, int[] hashes, int[] signHashes) {
        final int mask = width - 1;
        for (int i = 0; i < depth; i++) {
            hashes[i] = mix(attrValue, seeds[i]) & mask;
            signHashes[i] = signHash(attrValue, seedsG[i]);
        }

    }
    static int mix(int x, int seed) {
        x ^= seed;
        x *= 0x85ebca6b;
        x ^= x >>> 13;
        x *= 0xc2b2ae35;
        return x ^ (x >>> 16);
    }
    static int signHash(int z, int seed) {
        z ^= seed;
        z *= 0xcc9e2d51;
        z ^= z >>> 13;
        z *= 0x1b873593;
        z ^= z >>> 16;
        return (z < 0) ? -1 : +1;
    }

    public int memory() {
        return depth  * width * 4;
    }



}
