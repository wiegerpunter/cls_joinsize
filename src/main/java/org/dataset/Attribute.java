package org.dataset;

import org.dataset.Distributions.Distribution;

public final class Attribute {
    public final int id;
    public final int cardinality;
    public final Distribution distribution;

    public Attribute(int id, int cardinality, Distribution distribution) {
        this.id = id;
        this.cardinality = cardinality;
        this.distribution = distribution;
    }
}
