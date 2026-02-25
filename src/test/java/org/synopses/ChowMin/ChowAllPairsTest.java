package org.synopses.ChowMin;

import org.junit.jupiter.api.Test;
import org.utils.Edge;

import java.util.ArrayList;

import static org.ChowLiu.computeMIs;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ChowAllPairsTest {
    private ArrayList<Edge> getChowLiu(){
        Edge e1 = new Edge(0,1);
        Edge e2 = new Edge(1, 2);
        Edge e3 = new Edge(2,3);
        Edge e4 = new Edge(1,4);
        ArrayList<Edge> chowLiu = new ArrayList<>();
        chowLiu.add(e1);
        chowLiu.add(e2);
        chowLiu.add(e3);
        chowLiu.add(e4);
        return chowLiu;
    }

    @Test
    void testChow2DMinSimple() {
        int numAttributes = 5;
        int ram = 3000;

        int[][] data = new int[2][];
        int[] record = new int[]{1,2,3,4,5};
        int[] rec2 = new int[]{1,2,3,5,4};
        data[0] = record;
        data[1] = rec2;
        double[][] MIs = computeMIs(data);
        ChowMinAllPairs c = new ChowMinAllPairs(numAttributes,3, ram,MIs);
        c.add(record);
        c.add(rec2);

        int[] query = new int[]{1,2,3,4,5};
        int est1 = c.query(query);
        assertEquals(1, est1, "Unexpected estimate size " + est1);
        int[] query2 = new int[]{1,2,3,-1,-1};
        int est2 = c.query(query2);
        assertEquals(2, est2, "Unexpected estimate size " + est2);

        int[] query3 = new int[]{1,-1,-1,4,-1};
        int est3 = c.query(query3);
        assertEquals(1, est3, "Unexpected estimate size " + est3);
    }

}
