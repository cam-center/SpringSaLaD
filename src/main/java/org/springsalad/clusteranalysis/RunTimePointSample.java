package org.springsalad.clusteranalysis;

import java.util.List;

class RunTimePointSample {
	
	final String runStr; 
	final double timePointValue; 
	
	// FIXME pass in unmodifiable lists
	final List<Integer> clusterSizeList;
	final List<Cluster> clusterCompList;
	
	public RunTimePointSample(String runStr, double timePointValue, List<Integer> clusterSizeList, List<Cluster> clusterCompList) {
		this.runStr = runStr;
		this.timePointValue = timePointValue;
		this.clusterSizeList = clusterSizeList;
		this.clusterCompList = clusterCompList;
	}
	
	
}
