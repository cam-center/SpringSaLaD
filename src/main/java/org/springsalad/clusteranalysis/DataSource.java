package org.springsalad.clusteranalysis;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

class DataSource {
	private final String dataFolder;
	private final int nf;
	private final String clusterInputFileFormat;
	
	private final List<String> molNames;
	
	// FIXME make unmodifiable
	// FIXME maybe use arrays instead?
	public final List<Integer> runNumberList;
	public final List<Double> timePointValueList;
	
	// FIXME make unmodifiable
	public final List<Iterable<RunTimePointSample>> byTimeThenByRun;
	public final List<Iterable<RunTimePointSample>> byRunThenByTime;
	
	DataSource (String dataFolder, List<String> molNames,
			double startTime, double endTimeInclusive, double timeStep,
			int firstRunIndex, int lastRunIndexInclusive) {

		this.dataFolder = dataFolder;
		nf = Math.max(0,BigDecimal.valueOf(timeStep).stripTrailingZeros().scale());
		clusterInputFileFormat = "Run%d\\Clusters_Time_%."+nf+"f.csv";
		
		this.molNames = molNames;
		
		long totRunNum = lastRunIndexInclusive - firstRunIndex + 1;
		runNumberList = IntStream.iterate(firstRunIndex, i -> i + 1).limit(totRunNum).boxed().collect(toList());
		long totTPVNum = (long)((endTimeInclusive - startTime)/timeStep) +1;
		timePointValueList = DoubleStream.iterate(startTime, tpv -> tpv + timeStep).limit(totTPVNum).boxed().collect(toList());
		
		byTimeThenByRun = timePointValueList.stream().map(tpv -> new SingleTimeAllRunsIterable(tpv)).collect(toList());
		byRunThenByTime = runNumberList.stream().map(run -> new SingleRunAllTimeIterable(run)).collect(toList());
	}
	
	public Iterable<RunTimePointSample> getSingleRunAllTimes(int run) {
		//FIXME check argument
		return byRunThenByTime.get(runNumberList.indexOf(run));
	}
	
	public Iterable<RunTimePointSample> getSingleTimeAllRuns(double tpv) {
		// FIXME check argument
		// FIXME use doubles comparator
		return byRunThenByTime.get(timePointValueList.indexOf(tpv));
	}
	
	private class SingleTimeAllRunsIterable implements Iterable<RunTimePointSample>{
		private double tpv;
		private SingleTimeAllRunsIterable(double tpv) {
			this.tpv = tpv;
		}
		
		@Override
		public SingleTimeAllRunsIterator iterator() {
			return new SingleTimeAllRunsIterator(tpv);
		}
	}
	
	private class SingleTimeAllRunsIterator implements Iterator<RunTimePointSample>{
		private double tpv;
		private Iterator<Integer> runIterator;
		
		private SingleTimeAllRunsIterator(double tpv) {
			this.tpv = tpv ;
			runIterator = runNumberList.iterator();
		}
		
		@Override
		public boolean hasNext() {
			return runIterator.hasNext();
		}
		
		@Override
		public RunTimePointSample next() {
			int run = runIterator.next();
			return getRunTimePointSample(run, tpv);
		}
	}
	
	private class SingleRunAllTimeIterable implements Iterable<RunTimePointSample>{
		private int run;
		private SingleRunAllTimeIterable(int run) {
			this.run = run;
		}
		
		@Override
		public SingleRunAllTimeIterator iterator() {
			return new SingleRunAllTimeIterator(run);
		}
	}
	
	private class SingleRunAllTimeIterator implements Iterator<RunTimePointSample>{
		private int run;
		private Iterator<Double> timeIterator;
		
		private SingleRunAllTimeIterator(int run) {
			this.run = run;
			timeIterator = timePointValueList.iterator();
		}
		
		@Override
		public boolean hasNext() {
			return timeIterator.hasNext();
		}
		
		@Override
		public RunTimePointSample next() {
			double tpv = timeIterator.next();
			return getRunTimePointSample(run, tpv);			
		}
	}
	
	// if data comes from somewhere else, extract an abstract datasource class with this method as the only abstract method
	protected RunTimePointSample getRunTimePointSample(int run, double tpv) {
		// FIXME throw ioe exception upwards
		Path path = Paths.get(dataFolder, String.format(clusterInputFileFormat, run, tpv));
		try {			
			DataFrame rawFileDF = CSVHandler.readCSV(path);
			RunTimePointSample rtps = parseRawFileDF(run, tpv, rawFileDF);
			return rtps;
		}
		catch (IOException ioe) {
			
		}
		
		catch (ClassCastException cce){
            throw new ClassCastException(cce.getMessage()
                    + "\nData does not match expected format of String,Integer"
                    + "\nFile: " + path);
        }
        catch (IllegalStateException ise) {
        	throw new IllegalStateException(ise.getMessage() + "\nFile: " + path, ise);
        }
        // FIXME do we still need this?
        catch (IllegalArgumentException iae){ 
            throw new IllegalArgumentException(iae.getMessage() + "\nFile: " + path, iae);
        }
        catch (NoSuchElementException nsee){ //.next()
            throw new NoSuchElementException(nsee.getMessage() + "\nFile: " + path);
        }
		return null;
	}
	
	RunTimePointSample parseRawFileDF(int run, double tpv, DataFrame rawFileDF) {
		List<Integer> clusterSizeList = new ArrayList<>();
        List<Cluster> clusterCompList = new ArrayList<>();

        Iterator<Object[]> rawFileDFIterator = rawFileDF.iterator();

        //assembled clusterSizeList and clusterCompList
        Object[] dfRow;
        int numClusters = 0;
        while (rawFileDFIterator.hasNext()){
            dfRow = rawFileDFIterator.next();

            String label = (String)dfRow[0];
            if (label.equals("Total clusters")){
                numClusters = (Integer)dfRow[1];
            }
            if (label.equals("Size")) {
                int size = (Integer) dfRow[1];
                clusterSizeList.add(size);
                ClusterBuilder clusterBuilder = new ClusterBuilder(molNames, size);
                while (rawFileDFIterator.hasNext()) {
                    dfRow = rawFileDFIterator.next();
                    if (dfRow[0].equals("Cluster Index")){
                        break;
                    }
                    String molecule = (String) dfRow[0];
                    int count = (Integer) dfRow[1];
                    clusterBuilder.addToCluster(molecule, count);
                }
                clusterCompList.add(clusterBuilder.getCluster());
            }
        }
        IntStream.generate(()->1).limit(numClusters-clusterSizeList.size()).forEach(clusterSizeList::add);
		return new RunTimePointSample(String.format(ClusterStatsProducer.SINGLE_RUN_STR, run), 
										tpv, clusterSizeList, clusterCompList);
	}

}
