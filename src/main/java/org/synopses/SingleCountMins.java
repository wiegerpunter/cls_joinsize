package org.synopses;

import org.utils.Edge;

import java.util.ArrayList;
import java.util.HashMap;

public class SingleCountMins extends AbstractSynopsis {
    /*
     Pairwise summary for all attributes x attributes, plus a single CM for every attribute.
     Pairwise Summary is CM with a composite hash key.
     */


    CountMin[] singleCMs;
    public int d;
    public int w;

    public SingleCountMins(int numAttributes, int ram) {
        setting = "SingleCountMins";
        this.ram = ram;
        int[] param = getPars(ram, numAttributes);
        this.d = param[0];
        this.w = param[1];
        this.numAttributes = numAttributes;
        singleCMs = new CountMin[numAttributes];
        for (int i = 0; i < singleCMs.length; i++) {
            singleCMs[i] = new CountMin(d, w, i);
        }

    }

    public static int[] getPars(int ram, int numAttributes) {
        int d = 3;
        // number of CMs needed: numAttributes + numAttributes - 1
        int numCMs = Math.max(1,numAttributes - 1);
        int cells = ram / 4;
        int perCM = cells / (d * numCMs);
        int width = (int) perCM;
        width = Integer.highestOneBit(width);
        System.out.println("Single CountMin has parameters d: " + d + " w: " +width);

        return new int[]{d,width};
    }


    public int getd() {
        return d;
    }
    public int getw() {
        return w;
    }

    @Override
    public int numLatent(int[] query) {
        return 0;
    }

    @Override
    public int getNumSamples() {
        return 0;
    }

    @Override
    public void add(int[] record) {
        N += 1;
        for (int i = 0; i < numAttributes; i++) {
            singleCMs[i].add(record[i]);
        }
    }

    @Override
    public int query(int[] record) {
        ArrayList<Integer> predicates = new ArrayList<>();
        for (int i = 0; i < record.length; i++) {
            if (record[i] != -1) {
                predicates.add(i);
            }
        }

        if (predicates.isEmpty()) {
            return N;
        }

        if (predicates.size() == 1) {
            return singleCMs[predicates.get(0)].query(record[predicates.get(0)]);
        }

        throw new RuntimeException("Not supported for single CMs");
    }

    @Override
    public int memoryUsage() {
        int ram = 0;
        for (CountMin singleCM : singleCMs) {
            ram += singleCM.memory();
        }
       return ram;
    }

}
