package org.springsalad.clusteranalysis;
import java.util.SortedMap;

// no encapsulation
class TPStats {
    
    final String runStr;
    // FIXME make all these final (including the maps, once TimePoint is gone
    final double tpv;
    double acs;
    final double sd;
    double aco;
    
    // FIXME pass in unmodifiable maps
	final SortedMap<Integer,Double> sizeFreqMap;
	SortedMap<Integer,Double> sizeFotmMap; 
	final SortedMap<Cluster,Double> compFreqMap;
	    
    TPStats(String runStr, double tpv, double acs, double sd, double aco, 
    		SortedMap<Integer,Double> sizeFreqMap, SortedMap<Integer,Double> sizeFotmMap, 
    		SortedMap<Cluster,Double> compFreqMap){
    	this.runStr = runStr;
    	this.tpv = tpv;
    	this.acs = acs;
    	this.sd = sd;
    	this.aco = aco;
    	this.sizeFreqMap = sizeFreqMap;
    	this.sizeFotmMap = sizeFotmMap; 
    	this.compFreqMap = compFreqMap;
    }
}
