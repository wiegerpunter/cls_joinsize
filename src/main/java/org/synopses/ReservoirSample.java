package org.synopses;

import java.util.Random;

public class ReservoirSample extends AbstractSynopsis{
    private int[][] reservoir;
    private int size;
    private int count;
    Random rn;

    public ReservoirSample(int ram, int numAttributes) {
        setting = "Reservoir Sample";
        this.ram = ram;
        this.size = ram / (4 * numAttributes);
        this.count = 0;
        rn = new Random(0);
        reservoir = new int[size][numAttributes];
    }

    public void add(int[] rec) {
        if (count < size){
            reservoir[count] = rec;
        } else{
            int replace = rn.nextInt(count + 1);
            if (replace < size) {
                reservoir[replace] = rec;
            }
        }
        count++;
    }

    public int query(int[] query) {
        int queryResultCount = 0;
        for (int i = 0; i < size; i++) {
            boolean match = true;
            for (int j = 0; j < query.length; j++) {
                if (query[j] != -1 && query[j] != reservoir[i][j]) {
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

    public int memoryUsage() {
        int ram = 0;
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
    public int numLatent(int[] query) {
        return 0;
    }
}
