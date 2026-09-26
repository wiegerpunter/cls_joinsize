//package org.synopses.ChowMin;
//
//import org.junit.jupiter.api.Test;
//import org.utils.Edge;
//
//import static org.junit.jupiter.api.Assertions.*;
//
//import java.util.*;
//public class ChowM2DTest {
//
//    private ArrayList<Edge> getChowLiu(){
//        Edge e1 = new Edge(0,3);
//        Edge e2 = new Edge(0, 2);
//        Edge e3 = new Edge(0,1);
//        ArrayList<Edge> chowLiu = new ArrayList<>();
//        chowLiu.add(e1);
//        chowLiu.add(e2);
//        chowLiu.add(e3);
//        return chowLiu;
//    }
//
//
//    @Test
//    void testChow2DMinSimple() {
//        int numAttributes = 4;
//        int ram = 10000;
//        ChowM2D c = new ChowM2D(numAttributes, ram, getChowLiu());
//
//        int[] record = new int[]{1,2,3,4};
//        int[] rec2 = new int[]{1,2,3,5};
//        c.addIntArr(record);
//        c.addIntArr(rec2);
//
//        int[] query = new int[]{1,2,3,4};
//        int est1 = c.query(query);
//        assertEquals(1, est1, "Unexpected estimate size " + est1);
//        int[] query2 = new int[]{1,2,3,-1};
//        int est2 = c.query(query2);
//        assertEquals(2, est2, "Unexpected estimate size " + est2);
//    }
//
//    @Test
//    void testChow2DMinLatent() {
//        int numAttributes = 4;
//        int ram = 10000;
//        ChowM2D c = new ChowM2D(numAttributes, ram, getChowLiu());
//
//        int[] record = new int[]{1,2,3,4};
//        int[] rec2 = new int[]{1,2,3,5};
//        c.addIntArr(record);
//        c.addIntArr(rec2);
//
//        int[] query = new int[]{-1,2,3,4};
//        int est1 = c.query(query);
//        int numLatent = c.numLatent(query);
//        assertEquals(1,numLatent,"Unexpected numLatent" + numLatent);
//        assertEquals(1, est1, "Unexpected estimate size " + est1);
//        int[] query2 = new int[]{-1,2,3,-1};
//        int est2 = c.query(query2);
//        numLatent = c.numLatent(query2);
//        int mem = c.memoryUsage();
////        assertEquals(ram, mem, "Unexpected memory usage");
//        assertEquals(2, est2, "Unexpected estimate size " + est2);
//
//        assertEquals(1,numLatent,"Unexpected numLatent" + numLatent);
//    }
//
//    @Test
//    void testNumberOfSummaries() {
//        int numAttributes = 4;
//        int ram = 10000;
//
//        ChowM2D c = new ChowM2D(numAttributes, ram, getChowLiu());
//        assertEquals( numAttributes - 1, c.scheme.size(), "Wrong number of summaries.");
//    }
//
//    @Test
//    void testDimensions() {
//        int numAttributes = 4;
//        int ram = 1000;
//        ChowM2D c = new ChowM2D(numAttributes, ram, getChowLiu());
//
//
//        int[] record = new int[]{1,2,3,4};
//        HashSet<Integer> attrsInPath = new HashSet<>();
//        attrsInPath.add(1);
//        attrsInPath.add(2);
//        attrsInPath.add(3);
//        Set<Integer> latentAttrs = new HashSet<>();
//        latentAttrs.add(0);
//        c.constructDimensions(record, attrsInPath, latentAttrs);
//        // print the dimensions:
//
//        for (AttributeDimension d : c.attributeDimensions){
//            for (int j = 0; j < d.valuesPerCodomain.length; j++) {
//
//                System.out.println(d.attribute + ", j: " + j + ", val: " + Arrays.toString(d.valuesPerCodomain[j]));
//            }
//        }
//        System.out.println("----------------");
//    }
//
//    @Test
//        void testLoop() {
//        int numAttributes = 4;
//        int ram = 1000;
//        ChowM2D c = new ChowM2D(numAttributes, ram, getChowLiu());
//
//        int[] record = new int[]{1,2,3,4};
//        HashSet<Integer> attrsInPath = new HashSet<>();
//        attrsInPath.add(1);
//        attrsInPath.add(2);
//        attrsInPath.add(3);
//        Set<Integer> latentAttrs = new HashSet<>();
//        latentAttrs.add(0);
//        queryFactorsTest(c, record, latentAttrs, attrsInPath);
//
//    }
//       void queryFactorsTest(ChowM2D c, int[] record, Set<Integer> latentAttrs, HashSet<Integer> attrsInPath) {
//           c.constructDimensions(record, attrsInPath, latentAttrs);
//           int numDimensions = c.attributeDimensions.size();
//           int[] indices = new int[numDimensions];
//           while (true) {
//               int[][] k = new int[c.getd()][c.numAttributes];
//
//               for (int i = 0; i < numDimensions; i++) {
//                   AttributeDimension dim = c.attributeDimensions.get(i);
//                   int codomainIndex = indices[i];
//                   for (int j = 0; j < c.getd(); j++) {
//                       k[j][dim.attribute] = dim.valuesPerCodomain[j][codomainIndex];
//                   }
//               }
//
//
//               for (int j = 0; j < k.length; j++) {
//                   for (int ks = 0; ks < k[j].length; ks++) {
//                       System.out.println("d: " + j + ", a: " + ks + ", val: " + k[j][ks]);
//                   }
//               }
//
//               int pos = numDimensions - 1;
//               while (pos >= 0) {
//                   indices[pos]++;
//                   if (indices[pos] < c.attributeDimensions.get(pos).codomainSize) {
//                       break;
//                   }
//                   indices[pos] = 0;
//                   pos--;
//               }
//
//               if (pos < 0) {
//                   break; // all combinations exhausted
//               }
//           }
//       }
//}
