package org.springsalad.clusteranalysis;

import java.util.SortedMap;

class TPStats_OLD {
	double acs;
    double aco;
    SortedMap<Integer,Double> sizeFotmMap; 
    
    TPStats_OLD(){
        this.acs = 0;
        this.aco = 0;
    }
    
    //old version
    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder("Time point cluster stats{");
        sb.append("\nacs = ").append(acs);
        sb.append("\naco = ").append(aco);
        sb.append("\nfotm = ").append(sizeFotmMap);
        sb.append("\n}");
        return sb.toString();
    }
}
