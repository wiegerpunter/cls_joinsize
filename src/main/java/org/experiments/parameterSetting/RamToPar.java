package org.experiments.parameterSetting;


import java.util.HashMap;

public class RamToPar {


    // Baseline hashmaps
    HashMap<Integer, double[]> ramToAdap = new HashMap<>();
    HashMap<Integer, int[]> ramToChowMin = new HashMap<>();


    int numAttrs;
    //int w;
    int d;
    int b;
    double parFactor;
    int[] ramVals;

    public RamToPar(int numAttrsToUse, int[] ramValsToUse) {
        this.numAttrs = numAttrsToUse;
        ramVals = ramValsToUse;
        runBaselines();
    }

    private void runBaselines() {
        for (int ram : ramVals) {
            double[] parsAdap = setParamsASH(ram);
            ramToAdap.put(ram, parsAdap);
        }
    }

    public void runChowMin(int depth) {
        d = depth;
        for (int ram : ramVals) {
            int[] parsChowMin = setParamsChowMin(ram);
            ramToChowMin.put(ram, parsChowMin);
        }
    }


    private double[] setParamsASH(int ram) {
        // memory usage of aSH sampling is sample size * 32 * numAttrs * 5
        //double sampleSize = (double) ram / (32 * numAttrs * 5);
        double sampleSize = (double) ram / (32 * numAttrs + 64*5 + 2*32); // 8 for the size of the Double array and Tis array
        return new double[]{sampleSize};
    }

    public int[] getParamsAdapSampling(int ram, double ingestBuffer) {
        if (ramToAdap.containsKey(ram)) {
            double[] info = ramToAdap.get(ram);
            int sampleSize = (int) info[0];
            double ramPerRec = ram / (double) sampleSize;
//            int bufferSize = (int) Math.max(1, 8000000/ramPerRec);
            int bufferSize = (int) Math.max((ingestBuffer * sampleSize), 1);
            return new int[]{sampleSize - bufferSize, bufferSize, (int) (10*ingestBuffer)};
        } else {
            throw new IllegalArgumentException("RAM not found in ramToAdap");
        }
    }


    public int[] setParamsChowMin(int ram) {
        // number of CMs needed: numAttributes + numAttributes - 1
        int numCMs = numAttrs * numAttrs/2 - numAttrs/2;
        int cells = ram / 32;
        int perCM = cells / (d * numCMs);
        int width = (int) Math.sqrt(perCM);
        System.out.println("Chow M All pairs has parameters d: " + d + " w: " +width);

        return new int[]{d,width};
    }

    public int[] getParamsChowMin(int ram) {
        if (ramToChowMin.containsKey(ram)) {
            return ramToChowMin.get(ram);
        } else {
            throw new IllegalArgumentException("ram not found in ramToChowMin");
        }
    }
}
