package org.dataset.Distributions;

import java.util.Random;

public class ZipfDistribution implements Distribution {
    private final double[] cdf;

    public ZipfDistribution(int k, double skew) {
        this.cdf = new double[k];
        double sum = 0.0;
        for (int i = 1; i <= k; i++) sum += 1.0 / Math.pow(i, skew);
        double acc = 0.0;
        for (int i = 0; i < k; i++) {
            acc += (1.0 / Math.pow(i + 1, skew)) / sum;
            cdf[i] = acc;
        }
    }

    public int sample(Random rng) {
        double x = rng.nextDouble();
        for (int i = 0; i < cdf.length; i++)
            if (x <= cdf[i]) return i;
        return cdf.length - 1;
    }
}

