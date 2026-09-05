package org.synopses.baselines;

import org.synopses.AbstractSynopsis;

public class readFile extends AbstractSynopsis {
    public readFile() {

        setting = "readFile";

    }

    @Override
    public void addIntArr(int[] record) {
        int id = (int) record[0];
    }

    @Override
    public int query(int[] record) {
        return 0;
    }

    @Override
    public int memoryUsage() {
        return 0;
    }

    @Override
    public int getd() {
        return 0;
    }

    @Override
    public int getw() {
        return 0;
    }

    @Override
    public int numLatent(int[] query) {
        return 0;
    }

    @Override
    public int getNumSamples() {
        return 0;
    }

    public long getMemoryUsage() {
        //double v = 1 / (1 - bufferFactor);
        return 0;
//        return (long) maxSize * 32 * (numAttrs + 5); // size * (size of record + size of Double array + Tis array)
    }

    public void printParams() {
        System.out.println();
    }
}
