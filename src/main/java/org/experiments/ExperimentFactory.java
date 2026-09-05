package org.experiments;

import org.experiments.baselines.aSHExperiment;
import org.experiments.baselines.readFileExperiment;
import org.experiments.chowMin.chowMinExperiment;

import java.util.HashMap;
import java.util.Map;

public class ExperimentFactory {
    private static final Map<String, Experiment> experiments = new HashMap<>();

    static {
//        experiments.put("ReservoirSampling", new ReservoirExperiment());
        experiments.put("aSH", new aSHExperiment());
        experiments.put("chowMin", new chowMinExperiment());
//        experiments.put("Hydra", new HydraExperiment());
        experiments.put("readFile", new readFileExperiment());


    }

    public static Experiment getExperiment(String experimentName) {
        return experiments.getOrDefault(experimentName, null);
    }
}