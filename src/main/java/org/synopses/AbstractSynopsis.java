package org.synopses;


public abstract class AbstractSynopsis {
    public int ram;
    public int numAttributes;
    public int N = 0;
    public String setting;

    public AbstractSynopsis() {
    }

    public abstract void add(int[] record);

    public abstract int query(int[] record);

    public abstract int memoryUsage();

    public abstract int getd();

    public abstract int getw();

    public abstract int numLatent(int[] query);

    public abstract int getNumSamples();
}
