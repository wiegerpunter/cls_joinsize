package org.experiments.chowMin;

import org.experiments.Experiment;
import org.experiments.RunSingleTableExperiment;
import org.experiments.parameterSetting.RamToPar;
import org.synopses.ChowMin.ChowMinAllPairs;
import org.utils.Config;

import java.io.IOException;
import java.util.Arrays;

import static org.experiments.RunSingleTableExperiment.estimateMIs;

public class chowMinExperiment implements Experiment {
    private RunSingleTableExperiment runExperiments;
    @Override
    public void run(int ram, RamToPar rtp, int repetition, Config config) throws IOException {

        int[] params = rtp.getParamsChowMin(ram);
        System.out.println("Running chowMin with parameters: " + Arrays.toString(params));

        double[][] estimatedMIs = estimateMIs(runExperiments.data, ram, params);
        ChowMinAllPairs cm = new ChowMinAllPairs(ram, config.numStoredAttributes, params);
        cm.setMIs(estimatedMIs, true);
        cm.printParams();
        runExperiments.runSynopsis(cm, repetition);
    }



    @Override
    public void setRunExperiments(RunSingleTableExperiment runExperiments) {
        this.runExperiments = runExperiments;
    }
}
