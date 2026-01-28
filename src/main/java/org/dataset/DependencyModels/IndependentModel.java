package org.dataset.DependencyModels;

import org.dataset.Attribute;

import java.util.Random;

public class IndependentModel implements DependencyModel {
    private final Attribute[] attrs;

    public IndependentModel(Attribute[] attrs) {
        this.attrs = attrs;
    }

    @Override
    public void generateRecord(int[] record, Random rng) {
        for (int i = 0; i < attrs.length; i++) {
            record[i] = attrs[i].distribution.sample(rng);
        }
    }
}
