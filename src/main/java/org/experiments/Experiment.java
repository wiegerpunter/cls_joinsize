package org.experiments;

import org.experiments.parameterSetting.RamToPar;
import org.utils.Config;

import java.io.IOException;

public interface Experiment {
    void run(int ram, RamToPar rtp, int repetition, Config config) throws IOException;
    void setRunExperiments(RunSingleTableExperiment runExperiments);
}
