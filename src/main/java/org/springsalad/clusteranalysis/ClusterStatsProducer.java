package org.springsalad.clusteranalysis;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

public class ClusterStatsProducer {
	// use these when creating time series holders, run time point samples and tp stats
	public static final String MEAN_RUN_STR = "MEAN_Run";
	public static final String OVERALL_RUN_STR = "OVERALL_Run";
	public static final String SINGLE_RUN_STR = "Run%d";
	
	
	//discarded starts
	/*
	 * public static void calculateAndWriteClusterStats_OLD(ClusterDataAggregation
	 * clusterDataAggregation) {
	 * 
	 * // For each run-timepoint snapshot, we calculate some cluster stats (some
	 * averages and some maps). // We then average the cluster stats to get a mean
	 * cluster stats of all runs at that timepoint. // The maps are written out for
	 * each snapshot. // The averages are compiled for each run and written out as
	 * time series. // We also calculate the cluster stats for an overall run, //
	 * after combining the clusters of all the runs at each timepoint.
	 * 
	 * 
	 * Collection<Trajectory> trajCollection =
	 * clusterDataAggregation.getTrajectories(); List<TimeSeriesHolder>
	 * trajAverageHoldersList = trajCollection.stream() .map(traj -> new
	 * TimeSeriesHolder(traj.getTrajNum())) .collect(toList());
	 * 
	 * TimeSeriesHolder meanTrajAverageHolder = new TimeSeriesHolder("Mean");
	 * 
	 * Trajectory overallTraj = clusterDataAggregation.getOverallTraj();
	 * TimeSeriesHolder overallAverageHolder = new TimeSeriesHolder("Overall");
	 * 
	 * for (double timePointValue: clusterDataAggregation.getTimePointValues()) {
	 * 
	 * List<TPStats> tpStatsList = new ArrayList<>(); Iterator<TimeSeriesHolder>
	 * trajAverageHoldersListIterator = trajAverageHoldersList.iterator();
	 * 
	 * for (Trajectory traj: trajCollection) { TimePoint timePoint =
	 * traj.getTimePoint(timePointValue); TPStats tpStats =
	 * calculateClusterStatsAtOneTimePoint(timePoint);
	 * 
	 * tpStatsList.add(tpStats); writeClusterMaps(tpStats);
	 * trajAverageHoldersListIterator.next().addAveragesToLists(tpStats); }
	 * 
	 * TPStats meanTpStats = calculateMeanTpStats(tpStatsList);
	 * writeClusterMaps(meanTpStats);
	 * meanTrajAverageHolder.addAveragesToLists(meanTpStats);
	 * 
	 * TimePoint overallTimePoint = overallTraj.getTimePoint(timePointValue);
	 * TPStats overallTpStats =
	 * calculateClusterStatsAtOneTimePoint(overallTimePoint);
	 * 
	 * writeClusterMaps(overallTpStats);
	 * overallAverageHolder.addAveragesToLists(overallTpStats);
	 * 
	 * } writeAverageHoldersList(trajAverageHoldersList);
	 * writeAverageHolder(meanTrajAverageHolder);
	 * writeAverageHolder(overallAverageHolder); }
	 * 
	 * private static void writeAverageHoldersList(List<TimeSeriesHolder>
	 * trajAverageHoldersList) { // FIXME this is not needed anymore
	 * 
	 * }
	 * 
	 * private static void writeAverageHolder(TimeSeriesHolder
	 * meanTrajAverageHolder) { // FIXME this is not needed anymore
	 * 
	 * } private static TPStats calculateMeanTpStats(List<TPStats> tpStatsList) { //
	 * FIXME this is not needed anymore return null; }
	 * 
	 * 
	 * private static TPStats calculateClusterStatsAtOneTimePoint(TimePoint tp) { //
	 * FIXME don't implement return null; }
	 * 
	 * 
	 * private static void writeClusterMaps(TPStats tpStats) { // FIXME this is not
	 * needed anymore
	 * 
	 * } //discarded ends
	 */	
	
	public static void calculateAndWriteClusterStats(DataSource dataSource, DataDestination dataDestination) {
		List<Integer> runNumList = dataSource.runNumberList;
		List<Double> timeInSeconds = dataSource.timePointValueList;
		Map<String,TimeSeriesHolder> timeSeriesHolderMap = runNumList.stream().collect(toMap(i->String.format(SINGLE_RUN_STR,i), 
																							i -> new TimeSeriesHolder(String.format(SINGLE_RUN_STR,i), 
																														timeInSeconds)));
		timeSeriesHolderMap.put(MEAN_RUN_STR, new TimeSeriesHolder(MEAN_RUN_STR, timeInSeconds));
		timeSeriesHolderMap.put(OVERALL_RUN_STR, new TimeSeriesHolder(OVERALL_RUN_STR, timeInSeconds));
		
		for(Iterable<RunTimePointSample> singleTPAllRuns: dataSource.byTimeThenByRun) {
			OverallTPBuilder overallTPBuilder = new OverallTPBuilder();
			MeanTPStatsBuilder meanTPStatsBuilder = new MeanTPStatsBuilder(); 
			
			for (RunTimePointSample runTimePointSample: singleTPAllRuns) {
				overallTPBuilder.addTP(runTimePointSample);
				TPStats tpStats = calculateClusterStatsAtOneTimePoint(runTimePointSample);
				dataDestination.storeSizeFreqFotm(tpStats);
				dataDestination.storeCompFreq(tpStats);
				timeSeriesHolderMap.get(tpStats.runStr).addAveragesToLists(tpStats);
				meanTPStatsBuilder.addTPStats(tpStats);
			}
			TPStats meanTPStats = meanTPStatsBuilder.getMeanTPStats();
			dataDestination.storeSizeFreqFotm(meanTPStats);
			dataDestination.storeCompFreq(meanTPStats);
			timeSeriesHolderMap.get(MEAN_RUN_STR).addAveragesToLists(meanTPStats);
			
			RunTimePointSample overallTP = overallTPBuilder.getOverallTP();
			TPStats overallTPStats = calculateClusterStatsAtOneTimePoint(overallTP);
			dataDestination.storeSizeFreqFotm(overallTPStats);
			dataDestination.storeCompFreq(overallTPStats);
			timeSeriesHolderMap.get(OVERALL_RUN_STR).addAveragesToLists(overallTPStats);
		}
		
		for(TimeSeriesHolder timeSeriesHolder: timeSeriesHolderMap.values()) {
			dataDestination.storeTimeSeriesHolder(timeSeriesHolder);
		}
	}
	
	// may want to move to within run time point sample
	private static TPStats calculateClusterStatsAtOneTimePoint(RunTimePointSample tpObj) {
		List<Integer> sizeList = tpObj.clusterSizeList;
		List<Cluster> compList = tpObj.clusterCompList;
		
		final double totNumClusters = sizeList.size();
        final double totNumMolecules = sizeList.stream().mapToInt(Integer::intValue).sum();
        double acs = totNumMolecules/totNumClusters;

        final SortedMap<Integer,Integer> sizeCountMap = sizeList.stream().collect(toMap(Function.identity(),
																		                i->1,
																		                (originalCount, one) -> originalCount + one,
																		                TreeMap::new));
        SortedMap<Integer,Double> sizeFreqMap = sizeCountMap.entrySet().stream().collect(toMap(Map.Entry::getKey, me -> me.getValue()/totNumClusters, (v1,v2)->v1, TreeMap::new));
        SortedMap<Integer,Double> sizeFotmMap = sizeCountMap.entrySet().stream().collect(toMap(Map.Entry::getKey, me -> me.getValue()*me.getKey()/totNumMolecules, (v1,v2)->v1, TreeMap::new));
        double aco = sizeFotmMap.entrySet().stream().mapToDouble(me -> me.getKey() * me.getValue()).sum();
        double variance = sizeCountMap.entrySet().stream().mapToDouble(me -> me.getKey() * me.getKey() * me.getValue()).sum()/totNumClusters - acs*acs;
        double scaledSampleVariance = variance * totNumClusters / (totNumClusters-1);
        double sd = Math.sqrt(scaledSampleVariance);
        SortedMap<Cluster,Integer> compCountMap = compList.stream().collect(toMap(Function.identity(),c->1,(originalCount,one)->originalCount+one,TreeMap::new));
        SortedMap<Cluster,Double> compFreqMap = compCountMap.entrySet().stream().collect(toMap(Map.Entry::getKey,
        																					me->(double) (me.getValue())/(sizeCountMap.get(me.getKey().size)),
        																					(v1,v2NeverOccurs)->v1,
        																				TreeMap::new));
        return new TPStats(tpObj.runStr, tpObj.timePointValue, acs, sd, aco,
        		sizeFreqMap, sizeFotmMap, compFreqMap);
	}
	
}

