package org.dataset.DependencyModels;

import org.dataset.Attribute;

import java.util.Random;

public class FunctionalDependencyModel implements DependencyModel {
    private final Attribute[] attrs;
    private final int parent;
    private final int[] children;
    private final int[][] mapping;

    public FunctionalDependencyModel(
            Attribute[] attrs,
            int parent,
            int[] children
    ) {
        this.attrs = attrs;
        this.parent = parent;
        this.children = children;
        this.mapping = new int[attrs[parent].cardinality][children.length];

        Random rng = new Random(42);
        for (int v = 0; v < mapping.length; v++)
            for (int j = 0; j < children.length; j++)
                mapping[v][j] = rng.nextInt(attrs[children[j]].cardinality);
    }

    @Override
    public void generateRecord(int[] record, Random rng) {
        int pv = attrs[parent].distribution.sample(rng);
        record[parent] = pv;
        for (int j = 0; j < children.length; j++) {
            record[children[j]] = mapping[pv][j];
        }
    }
}

