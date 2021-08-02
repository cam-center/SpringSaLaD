package org.springsalad.clusteranalysis;

public class ClusterFactory {
    String[] moleculeNames;
    public ClusterFactory(String[] moleculeNames){
        this.moleculeNames = moleculeNames;
    }

    //factory methods
    private int[] tmpComp;
    public void startCluster(){
        tmpComp = new int[moleculeNames.length];
    }
    public void addToCluster(String molName, int count){
        for (int i = 0; i < moleculeNames.length; i++){
            if (molName.equals(moleculeNames[i])){
                tmpComp[i] += count;
                return;
            }
        }
        throw new IllegalArgumentException("Unrecognized molecule type: " + molName);
    }
    public Cluster_OLD returnedVerifiedCluster(int supposedSize){
        Cluster_OLD c = new Cluster_OLD(moleculeNames, tmpComp);
        if (c.size != supposedSize){
            throw new IllegalArgumentException("Malformed cluster: given size does not match with given composition");
        }
        return c;
    }
}
