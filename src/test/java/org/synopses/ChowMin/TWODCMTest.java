//package org.synopses.ChowMin;
//
//import org.junit.jupiter.api.Test;
//import org.utils.Edge;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//import java.util.*;
//public class TWODCMTest {
//
//    @Test
//    void testTWODCMSimple() {
//        Edge e1 = new Edge(0,1);
//        Edge e2 = new Edge(1,2);
//        TWODCM c1 = new TWODCM(3, 10, e1);
//        TWODCM c2 = new TWODCM(3,10, e2);
//
//        int[] rec1 = new int[]{1,2,3};
//        int[] p1 = c1.getPredIdx(2, 1);
//        int[] p2 = c2.getPredIdx(2, 1);
//
//        assertEquals(p1.length, p2.length, "Array not of same length");
//        for (int i = 0; i < p1.length; i++) {
//            assertEquals(p1[i], p2[i], "Hash of row " + i + " not the same." );
//        }
//    }
//
//    @Test
//    void testMarginalTWODCM() {
//        Edge e1 = new Edge(0,1);
//        Edge e2 = new Edge(1,2);
//        TWODCM c1 = new TWODCM(3, 10, e1);
//        TWODCM c2 = new TWODCM(3,10, e2);
//
////        int[] rec1 = new int[]{1,2,3};
////        int[] rec2 = new int[]{0,2,0};
////        int[] rec3 = new int[]{0,0,0};
//        c1.add(1,2);
//        c1.add(0,2);
//        c1.add(0,0);
//        c2.add(2,3);
//        c2.add(2,0);
//        c2.add(0,0);
//        assertEquals(c1.queryMarginal(2,1), c2.queryMarginal(2,1),"Marginals not the same.");
//    }
//}
