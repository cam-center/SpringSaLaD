package org.springsalad.clusteranalysis;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;

public class TrajStats {
    List<Double> acsList;
    List<Double> acoList;
    List<SortedMap<Integer,Double>> fotmList;

    public TrajStats(){
        acsList = new ArrayList<>();
        acoList = new ArrayList<>();
        fotmList = new ArrayList<>();
    }

    @Override
    public String toString(){
        StringBuilder sb = new StringBuilder("Time point cluster stats{");
        sb.append("\nacsList = ").append(acsList);
        sb.append("\nacoList = ").append(acoList);
        sb.append("\nfotmList = ").append(fotmList);
        sb.append("\n}");
        return sb.toString();
    }
}
