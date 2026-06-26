package org.synopses;

import java.util.Objects;
import java.util.Random;

public class ReservoirSampleString extends AbstractSynopsis{
    private final String[][] reservoir;
    private final int size;
    private int count;
    Random rn;

    public ReservoirSampleString(int ram, int numAttributes, int seed) {
        setting = "Reservoir Sample";
        this.ram = ram;
        this.size = ram / (4 * numAttributes);
        this.count = 0;
        rn = new Random(seed);
        reservoir = new String[size][numAttributes];
    }

    public void add(String[] rec) {
        if (count < size){
            reservoir[count] = rec.clone();
        } else{
            int replace = rn.nextInt(count + 1);
            if (replace < size) {
                reservoir[replace] = rec.clone();
            }
        }
        count++;
    }

    public int query(String[] query) {
        int queryResultCount = 0;
        for (int i = 0; i < size; i++) {
            boolean match = true;
            for (int j = 0; j < query.length; j++) {
                if (!Objects.equals(query[j], "-1") && !Objects.equals(query[j], reservoir[i][j])) {
                    match = false;
                    break;
                }
            }
            if (match) {
                queryResultCount++;
            }
        }
        // scale result by the size of the reservoir
        return (int) (queryResultCount * (Math.max((double) count /size, 1)));
    }

    @Override
    public void add(int[] record) {

    }

    @Override
    public int query(int[] record) {
        return 0;
    }

    public int memoryUsage() {
        int ram;
        ram = size * reservoir[0].length * 4;
        return ram;
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
    public int getNumSamples() {
        return size;
    }

    @Override
    public int numLatent(int[] query) {
        return 0;
    }
}
