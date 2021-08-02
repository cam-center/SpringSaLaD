package org.springsalad.clusteranalysis;

import java.util.ArrayList;
import java.util.List;

class OverallTPBuilder {
	double tpv;
	boolean tpvNotSetYet;
	List<Integer> clusterSizeList;
	List<Cluster> clusterCompList;
	
	OverallTPBuilder() {
		tpvNotSetYet = true;
		clusterSizeList = new ArrayList<>();
		clusterCompList = new ArrayList<>();
	}
	
	void addTP(RunTimePointSample runTimePointSample) {
		if (tpvNotSetYet) {
			tpv = runTimePointSample.timePointValue;
			tpvNotSetYet = false;
		}
		else {
			// FIXME double comparator
			assert runTimePointSample.timePointValue == tpv;
		}
		clusterSizeList.addAll(runTimePointSample.clusterSizeList);
		clusterCompList.addAll(runTimePointSample.clusterCompList);
	}
	
	RunTimePointSample getOverallTP() {
		return new RunTimePointSample(ClusterStatsProducer.OVERALL_RUN_STR, tpv, clusterSizeList, clusterCompList);
	}
}
