//package org.synopses.ChowMin;
//
//import org.junit.jupiter.api.Test;
//import org.utils.Edge;
//
//import java.util.ArrayList;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//
//public class ChowM2DVarElimTest {
//    private ArrayList<Edge> getChowLiu(){
//        Edge e1 = new Edge(0,1);
//        Edge e2 = new Edge(1, 2);
//        Edge e3 = new Edge(3,4);
//        Edge e4 = new Edge(2,3);
//        ArrayList<Edge> chowLiu = new ArrayList<>();
//        chowLiu.add(e1);
//        chowLiu.add(e2);
//        chowLiu.add(e3);
//        chowLiu.add(e4);
//        return chowLiu;
//    }
//
//    @Test
//    void testChow2DMinSimple() {
//        int numAttributes = 5;
//        int ram = 300;
//        ChowM2DVarElim c = new ChowM2DVarElim(numAttributes,3, ram, getChowLiu());
//
//        int[] record = new int[]{1,2,3,4,5};
//        int[] rec2 = new int[]{1,2,3,5,4};
//
//        int[] rec3 = new int[]{1,2,3,5,4};
//        c.addIntArr(record);
//        c.addIntArr(rec2);
//        c.addIntArr(rec3);
//
//        int[] query = new int[]{1,2,3,4,5};
//        int est1 = c.query(query);
//        assertEquals(1, est1, "Unexpected estimate size " + est1);
////        int[] query2 = new int[]{1,-1,-1,-1,4};
////        int est2 = c.query(query2);
////        assertEquals(2, est2, "Unexpected estimate size " + est2);
//        int[] query3 = new int[]{1,-1,-1,-1,-1};
//        int est3 = c.query(query3);
//        assertEquals(3, est3, "Unexpected estimate size " + est3);
//
//    }
//
//}
