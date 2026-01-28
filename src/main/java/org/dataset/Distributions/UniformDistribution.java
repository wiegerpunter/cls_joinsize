package org.dataset.Distributions;

import java.util.Random;

public class UniformDistribution implements Distribution {
    private final int k;

    public UniformDistribution(int k) {
        this.k = k;
    }

    public int sample(Random rng) {
        return rng.nextInt(k);
    }
}

