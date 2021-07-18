package org.springsalad.clusteranalysis;
import java.util.SortedMap;

// no encapsulation
public class TpStats {
    double acs; double aco; SortedMap<Integer, Double> foTM;
    public TpStats(){
        this.acs = 0;
        this.aco = 0;
    }

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder("Time point cluster stats{");
        sb.append("\nacs = ").append(acs);
        sb.append("\naco = ").append(aco);
        sb.append("\nfotm = ").append(foTM);
        sb.append("\n}");
        return sb.toString();
    }
}
