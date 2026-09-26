//package org.synopses.ChowMin;
//
//
//import org.junit.jupiter.api.Test;
//import org.utils.Edge;
//
//import static org.junit.jupiter.api.Assertions.*;
//import static org.synopses.ChowMin.ChowM2D.buildFactorization;
//
//import java.util.*;
//
//class BuildFactorizationTest {
//
//    @Test
//    void testBuildFactorizationSimplePath() {
//        // Build adjacency list
//        List<List<Integer>> adj = new ArrayList<>();
//        for (int i = 0; i < 5; i++) {
//            adj.add(new ArrayList<>());
//        }
//
//        // Undirected edges
//        addEdge(adj, 0, 1);
//        addEdge(adj, 1, 2);
//        addEdge(adj, 2, 3);
//        addEdge(adj, 1, 4);
//
//        Set<Integer> subtree = Set.of(1, 2, 3);
//        int root = 1;
//
//        List<Edge> factors = buildFactorization(subtree, root, adj);
//
//        Set<Edge> expected = Set.of(
//                new Edge(1, 1),
//                new Edge(1, 2),
//                new Edge(2, 3)
//        );
//
//        assertEquals(expected.size(), factors.size(),
//                "Unexpected number of factors");
//
//        assertTrue(factors.containsAll(expected),
//                "Factorization does not match expected Chow–Liu path");
//
//        // Ensure no node outside subtree appears
//        for (Edge e : factors) {
//            assertTrue(subtree.contains(e.getS()),
//                    "Edge contains node outside subtree");
//            assertTrue(subtree.contains(e.getT()),
//                    "Edge contains node outside subtree");
//        }
//    }
//
//    @Test
//    void testRootNotInSubtreeThrows() {
//        List<List<Integer>> adj = new ArrayList<>();
//        for (int i = 0; i < 3; i++) {
//            adj.add(new ArrayList<>());
//        }
//
//        addEdge(adj, 0, 1);
//        addEdge(adj, 1, 2);
//
//        Set<Integer> subtree = Set.of(1, 2);
//        int root = 0;
//
//        RuntimeException ex = assertThrows(
//                RuntimeException.class,
//                () -> buildFactorization(subtree, root, adj)
//        );
//
//        assertEquals("Subtree has no root.", ex.getMessage());
//    }
//
//    private static void addEdge(List<List<Integer>> adj, int u, int v) {
//        adj.get(u).add(v);
//        adj.get(v).add(u);
//    }
//}
