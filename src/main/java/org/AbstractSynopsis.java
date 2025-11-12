package org;


public abstract class AbstractSynopsis {
    public int ram;
    int numAttributes;
    public int N = 0;
    String setting;

    public AbstractSynopsis() {}

    public abstract void add(int[] record);

    public abstract int query(int[] record);

    public abstract int memoryUsage();

    public abstract int getd();
    public abstract int getw();

}
