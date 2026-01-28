package org.dataset.DependencyModels;

import java.util.Random;

public interface DependencyModel {
    void generateRecord(int[] record, Random rng);
}
