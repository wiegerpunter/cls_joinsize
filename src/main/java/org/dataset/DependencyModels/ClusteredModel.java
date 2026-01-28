package org.dataset.DependencyModels;

import org.dataset.Attribute;

import java.util.Random;

public class ClusteredModel implements DependencyModel {
    private final Attribute[] attrs;
    private final int[][] clusters;
    private final double noise;

    public ClusteredModel(Attribute[] attrs, int[][] clusters, double noise) {
        this.attrs = attrs;
        this.clusters = clusters;
        this.noise = noise;
    }

    @Override
    public void generateRecord(int[] record, Random rng) {
        // default: independent
        for (int i = 0; i < attrs.length; i++) {
            record[i] = attrs[i].distribution.sample(rng);
        }

        // enforce cluster correlation
        for (int[] cluster : clusters) {
            int base = attrs[cluster[0]].distribution.sample(rng);
            for (int i : cluster) {
                if (rng.nextDouble() < noise) {
                    record[i] = attrs[i].distribution.sample(rng);
                } else {
                    record[i] = base % attrs[i].cardinality;
                }
            }
        }
    }
}

