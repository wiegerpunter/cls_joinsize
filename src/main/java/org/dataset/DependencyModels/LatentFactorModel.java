package org.dataset.DependencyModels;

import org.dataset.Attribute;

import java.util.Random;

public class LatentFactorModel implements DependencyModel {
    private final Attribute[] attrs;
    private final int latentCardinality;
    private final double influence;

    public LatentFactorModel(Attribute[] attrs, int latentCardinality, double influence) {
        this.attrs = attrs;
        this.latentCardinality = latentCardinality;
        this.influence = influence;
    }

    @Override
    public void generateRecord(int[] record, Random rng) {
        int latent = rng.nextInt(latentCardinality);

        for (int i = 0; i < attrs.length; i++) {
            if (rng.nextDouble() < influence) {
                record[i] = latent % attrs[i].cardinality;
            } else {
                record[i] = attrs[i].distribution.sample(rng);
            }
        }
    }
}

