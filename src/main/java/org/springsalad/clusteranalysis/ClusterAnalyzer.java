package org.springsalad.clusteranalysis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClusterAnalyzer {
    
    private DataSource dataSource;    
    private DataDestination dataDestination;
    
    public static void main(String[] args) throws IOException{
    	String dataFolder = "C:\\Users\\imt_w\\Documents\\SpringSalad\\Clustering_tutorial_01\\Clustering_tutorial_01_SIMULATIONS\\Simulation3_SIM_FOLDER\\data";
    	ClusterAnalyzer ca = new ClusterAnalyzer(dataFolder, 0, 2);
    	ca.calculateAndWriteClusterStats();
    }
	
	public ClusterAnalyzer(String dataFolder, int startIndex, int finishIndexInclusive) throws IOException{
        Pattern simNamePattern = Pattern.compile(".*(_SIM)_FOLDER");
        Matcher simNameMatcher = simNamePattern.matcher(dataFolder);
        if (simNameMatcher.find()){
            String simfile = dataFolder.substring(0,simNameMatcher.end(1)) + ".txt";
            SimFile simFileObj = new SimFile(simfile);
            
            double[] timeStats = simFileObj.getTimeStats();
            final double tTotal = timeStats[0];
            final double dtData = timeStats[3];
            
            final int numOfRuns = simFileObj.getNumRuns();
            if (finishIndexInclusive >= numOfRuns) {
            	throw new IllegalArgumentException("Unable to compute cluster stats: Last run index exceeds total number of runs");
            }           
            //FIXME create a dataSrcDest obj so that the parameters are only given once?
            List<String> molNames = new ArrayList<>(simFileObj.getMolecules().keySet());
            dataSource = new DataSource(dataFolder, molNames, 
            		0, tTotal, dtData, startIndex, finishIndexInclusive);
            dataDestination = new DataDestination(dataFolder, molNames, dtData);
            
        }
        else {
        	throw new IllegalArgumentException(
        			"Datafolder is not located under a SIM_FOLDER that has an accompanying SIM_txt\nDatafolder: " + dataFolder);
        }
    }
    
    public void calculateAndWriteClusterStats() throws IOException{
    	ClusterStatsProducer.calculateAndWriteClusterStats(dataSource, dataDestination);
    }
}
