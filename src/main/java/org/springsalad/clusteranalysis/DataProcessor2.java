/*
	To add more default data processing (that happens right after a Simulation has
	finished running), write your own calculation class and call it from this class.
 */

package org.springsalad.clusteranalysis;

import java.io.IOException;

import org.springsalad.dataprocessor.DataProcessor;

public class DataProcessor2 extends DataProcessor{
	private ClusterAnalyzer clusterAnalyzer;
	
	public DataProcessor2(String allSimsFolder, String simulationName) {
		super(allSimsFolder, simulationName);
	}
	
	@Override
	public void computeAllTimePointAverages(int startIndex, int endIndexInclusive) {
		super.computeAllTimePointAverages(startIndex, endIndexInclusive);
		
		try {
			clusterAnalyzer = new ClusterAnalyzer(dataFolder, startIndex, endIndexInclusive);
			clusterAnalyzer.calculateAndWriteClusterStats();
		}
		catch(IOException | IllegalArgumentException exception) {
			ExceptionDisplayer.justDisplayException("Unable to calculate cluster stats.", exception);
		}
	}

}
