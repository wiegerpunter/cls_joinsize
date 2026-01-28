package org.dataset.Distributions;

import java.util.Random;

public interface Distribution {
    int sample(Random rng);
}
