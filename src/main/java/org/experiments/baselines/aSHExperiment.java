package org.experiments.baselines;

import org.experiments.Experiment;
import org.experiments.RunSingleTableExperiment;
import org.experiments.parameterSetting.RamToPar;
import org.synopses.aSH;
import org.utils.Config;

import java.io.IOException;
import java.util.Arrays;

public class aSHExperiment implements Experiment {
    private RunSingleTableExperiment runExperiments;
    @Override
    public void run(int ram, RamToPar rtp, int repetition, Config config) throws IOException {

        int[] params = rtp.getParamsAdapSampling(ram, config.bufferASH);
        System.out.println("Running Adaptive S+H with parameters: " + Arrays.toString(params));
        aSH ash = new aSH(ram, config.numStoredAttributes, params, config.useBufferInQuery,  config.bufferASH, repetition);
        ash.printParams();
        runExperiments.runSynopsis(ash, repetition);
    }

    @Override
    public void setRunExperiments(RunSingleTableExperiment runExperiments) {
        this.runExperiments = runExperiments;
    }
}
