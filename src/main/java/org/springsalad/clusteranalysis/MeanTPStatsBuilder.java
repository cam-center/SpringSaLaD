package org.springsalad.clusteranalysis;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

class MeanTPStatsBuilder {
	
	private double tpv;
	private boolean tpvNotSetYet;
	private double acsSum, acoSum;
	private SortedMap<Integer,Double> sizeFreqMap, sizeFotmMap; 
	private SortedMap<Cluster,Double> compFreqMap;
	private List<Double> acsList;
	
	MeanTPStatsBuilder() {
		// defaults
		tpvNotSetYet = true;
		acsSum = acoSum = 0;
		sizeFreqMap = new TreeMap<>();
		sizeFotmMap = new TreeMap<>();
		compFreqMap = new TreeMap<>();
		acsList = new ArrayList<>();
	}
	
	void addTPStats(TPStats tpStats) {
		if (tpvNotSetYet) {
			tpv = tpStats.tpv;
			tpvNotSetYet = false;
		}
		else {
			// FIXME double comparator
			assert tpStats.tpv == tpv;
		}
		acsSum += tpStats.acs;
		acsList.add(tpStats.acs);
		acoSum += tpStats.aco;
		tpStats.sizeFreqMap.forEach((size,freq) -> sizeFreqMap.merge(size,freq, (oldFreq, newFreq) -> oldFreq + newFreq));
		tpStats.sizeFotmMap.forEach((size,fotm) -> sizeFotmMap.merge(size,fotm, (oldFotm, newFotm) -> oldFotm + newFotm));
		tpStats.compFreqMap.forEach((cluster,freq) -> compFreqMap.merge(cluster,freq, (oldFreq, newFreq) -> oldFreq + newFreq));
	}
	
	TPStats getMeanTPStats() {
		int numOfTP = acsList.size();
		double meanACS = acsSum/numOfTP;
		double meanACO = acoSum/numOfTP;
		double sdOfACS = acsList.stream().mapToDouble(acs->acs*acs).sum()/numOfTP - meanACS*meanACS;
		double scaledSD = sdOfACS * numOfTP / (numOfTP-1);
		
		sizeFreqMap.forEach((size,freqSum)->sizeFreqMap.put(size,freqSum/numOfTP));
		SortedMap<Integer,Double> sizeMeanFreqMap = sizeFreqMap;
		sizeFotmMap.forEach((size,fotmSum)->sizeFotmMap.put(size,fotmSum/numOfTP));
		SortedMap<Integer,Double> sizeMeanFotmMap = sizeFotmMap;
		compFreqMap.forEach((size,freqSum)->compFreqMap.put(size,freqSum/numOfTP));
		SortedMap<Cluster,Double> compMeanFreqMap = compFreqMap;
		return new TPStats(ClusterStatsProducer.MEAN_RUN_STR, tpv, meanACS, scaledSD, meanACO, sizeMeanFreqMap, sizeMeanFotmMap, compMeanFreqMap);
	}
}
