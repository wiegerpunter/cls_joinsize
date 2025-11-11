package org;

import java.util.ArrayList;
import java.util.HashMap;


public abstract class AbstractNaiveMin {
    int numAttributes;
    public int N = 0;
    String setting;

    public AbstractNaiveMin() {}

    public abstract void add(int[] record);

    public abstract int query(int[] record);

    public abstract int memoryUsage();

    public abstract int getd();
    public abstract int getw();

}
