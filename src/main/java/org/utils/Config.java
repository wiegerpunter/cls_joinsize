package org.utils;

public class Config {
    public String datasetType;
    public int N;
    public int[] numAttributes;
    public int[] maxNumPredicates;
    public int seed;
    public String modelType;
    public int numQueries;
    public int[] ramVals;
    public String dataDistribution;
    public boolean statsData;
    public boolean printData;
    public int numClusters;
    public int[][] clusters;
    public double clusterNoise;
    public double latentInfl;
    public int latentCard;
    public int[] num_rows;
    public double[] ingest_buffers;

    public void setClusters(int a_i) {
        clusters = new int[numClusters][];
        if (!modelType.equals("ClusteredModel")) {
            return;
        }
        int numAttrsPerCluster = Math.max(1, (int) Math.floor((double) (numAttributes[a_i] - numClusters) /numClusters));
        int curAttrs = 0;
        for (int i = 0; i < numClusters; i++) {
            clusters[i] = new int[numAttrsPerCluster];
            for (int j = 0; j < numAttrsPerCluster; j++) {
                clusters[i][j] = curAttrs;
                curAttrs++;
            }
            curAttrs++;
        }
    }
}
