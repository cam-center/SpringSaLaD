package org.springsalad.clusteranalysis;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SortedMap;

import static java.util.stream.Collectors.toList;

class DataDestination {
	public static final String averagesFileName = "ACS_SD_ACO.csv", 
								sizeFreqFotmFileName = "Size_Freq_Fotm.csv", 
								sizeCompFreqFileName = "Size_Comp_Freq.csv";
	
	public static final String AvgFolderEXTENSION = "Cluster_stat/Averages",
								HisFolderEXTENSION = "Cluster_stat/Histograms",
								SffFolderEXTENSION = HisFolderEXTENSION + "/Size_Freq_Fotm",
								ScfFolderEXTENSION = HisFolderEXTENSION + "/Size_Comp_Freq";
	
	private final Path clusterAveragesFolder, sizeFreqFotmFolder, sizeCompFreqFolder; 
	private final int nf;
	private final String molTypeHeader;
	
	DataDestination(String dataFolder, List<String> molNames, double dtData) {	
		clusterAveragesFolder = Paths.get(dataFolder, AvgFolderEXTENSION);
		sizeFreqFotmFolder = Paths.get(dataFolder, SffFolderEXTENSION);
		sizeCompFreqFolder = Paths.get(dataFolder, ScfFolderEXTENSION);
		
		// FIXME deal with ioe appropriately
		try {
			Files.createDirectories(clusterAveragesFolder);
			Files.createDirectories(sizeFreqFotmFolder);
			Files.createDirectories(sizeCompFreqFolder);
		}
		catch (IOException ioe) {
			
		}
		
		nf = Math.max(0,BigDecimal.valueOf(dtData).stripTrailingZeros().scale());
		
		StringBuilder moleculeTypeHeaderSB = molNames.stream().collect(StringBuilder::new, (sb,str)->sb.append(str).append(","), StringBuilder::append);
		moleculeTypeHeaderSB.insert(0,"\"").replace(moleculeTypeHeaderSB.length()-1, moleculeTypeHeaderSB.length(), "\"");
		molTypeHeader = moleculeTypeHeaderSB.toString();

	}
	
	void storeSizeFreqFotm(TPStats tpStats) {
		
		SortedMap<Integer, Double> sizeFreqMap = tpStats.sizeFreqMap;
		SortedMap<Integer, Double> sizeFotmMap = tpStats.sizeFotmMap;
		// FIXME check if parallel
		
		Path runFolder = Paths.get(sizeFreqFotmFolder.toString(), tpStats.runStr);
		// FIXME handle ioe appropriately
		try {
			Files.createDirectories(runFolder);
			Path filePath = Paths.get(runFolder.toString(),tpStats.runStr+String.format("_%."+nf+"f_",tpStats.tpv)+sizeFreqFotmFileName);
			CSVHandler.writeCSV(filePath, new String[] {"Size", "Frequency", "Fraction of total molecules"}, 
								new ArrayList<>(sizeFreqMap.keySet()), new ArrayList<>(sizeFreqMap.values()), 
								new ArrayList<>(sizeFotmMap.values()));
		}
		catch(IOException ioe) {
			
		}
	}
	
	void storeCompFreq(TPStats tpStats) {
		SortedMap<Cluster, Double> compFreqMap = tpStats.compFreqMap;
		
		Path runFolder = Paths.get(sizeCompFreqFolder.toString(), tpStats.runStr);
		List<Integer> sizeList = compFreqMap.keySet().stream().map(cluster -> cluster.size).collect(toList());
		
		// FIXME handle ioe appropriately
		try {
			Files.createDirectories(runFolder);
			Path filePath = Paths.get(runFolder.toString(),tpStats.runStr+String.format("_%."+nf+"f_",tpStats.tpv)+sizeCompFreqFileName);
			CSVHandler.writeCSV(filePath, new String[] {"Size", molTypeHeader,"Frequency in clusters of the same size"}, 
								sizeList, new ArrayList<>(compFreqMap.keySet()), new ArrayList<>(compFreqMap.values()));
		}
		catch(IOException ioe) {
			
		}
	}
	
	void storeTimeSeriesHolder(TimeSeriesHolder timeSeriesHolder) {
		Path filePath = Paths.get(clusterAveragesFolder.toString(), timeSeriesHolder.runStr + "_" + averagesFileName);
		// FIXME handle ioe
		try {
			CSVHandler.writeCSV(filePath, new String[] {"Time (s)", "ACS", "SD", "ACO"}, 
					timeSeriesHolder.timeInSeconds, timeSeriesHolder.acsList, timeSeriesHolder.sdList, timeSeriesHolder.acoList);
		}
		catch (IOException ioe) {
			
		}
	}
	
}
