package org.experiments.baselines;

import org.experiments.Experiment;
import org.experiments.RunSingleTableExperiment;
import org.experiments.parameterSetting.RamToPar;
import org.synopses.baselines.readFile;
import org.utils.Config;

import java.io.IOException;

public class readFileExperiment implements Experiment {
    private RunSingleTableExperiment runExperiments;

    @Override
    public void run(int ram, RamToPar rtp, int repetition, Config config) throws IOException {

        readFile readFile = new readFile();
        runExperiments.runSynopsis(readFile, repetition);
    }

    @Override
    public void setRunExperiments(RunSingleTableExperiment runExperiments) {
        this.runExperiments = runExperiments;
    }
}