package org.springsalad.clusteranalysis;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.TreeMap;

public class TimePoint {
    /*
    public static void main(String[] args) {
        TimePoint.moleculesNames = new String[]{"Nck", "NWASP", "nephrin"};
        Cluster.molecules = TimePoint.moleculesNames;
        TimePoint.nf = 4;

        Path inpath = Paths.get("C:\\Users\\imt_w\\Documents\\SpringSalad\\Clustering_tutorial_01\\Clustering_tutorial_01_SIMULATIONS\\Simulation0_SIM_FOLDER\\data\\Run0");
        Path inpath2 = Paths.get("C:\\Users\\imt_w\\Downloads\\MyTestFolder");
        TimePoint timePoint1 = new TimePoint(0.0100, inpath2);
        System.out.println(timePoint1.calculateClusterStats());
    }
     */


    //<editor-fold, defaultstate = "collapsed", desc = "supposedly 'private' constructor and builder setters">
    // inpath already has a run number!
    double timePointValue;
    Path inpath;
    TimePoint(double timePointValue, Path inpath){
        this.timePointValue = timePointValue;
        this.inpath = inpath;
    }
    int nf;
    public TimePoint setNf(int nf){
        this.nf = nf;
        inpath = Paths.get(inpath.toString(), String.format("Clusters_Time_%."+nf+"f.csv",timePointValue));
        return this;
    }
    boolean withMonomer;
    public TimePoint setWithMonomer(boolean withMonomer){
        this.withMonomer = withMonomer;
        return this;
    }
    String[] moleculesNames;
    public TimePoint setMoleculeNames(String[] moleculeNames){
        this.moleculesNames = moleculeNames;
        return this;
    }
    ClusterFactory clusterFactory;
    public TimePoint finishInitializing() throws IOException{
        this.clusterFactory = new ClusterFactory(moleculesNames);
        loadDataFromFile();
        return this;
    }
    //</editor-fold>

    List<Integer> clusterSizeList;
    List<Integer> clusterSizeListWithMonomer;
    List<Cluster_OLD> clusterCompList;
    // need to edit this
    private void loadDataFromFile() throws IOException {
        if (clusterSizeList != null){
            return;
        }
        clusterSizeList = new ArrayList<>();
        clusterCompList = new ArrayList<>();

        DataFrame inputTable = CSVHandler.readCSV(inpath);
        Iterator<Object[]> inputTableIterator = inputTable.iterator();

        //assembled clusterSizeList and clusterCompList
        Object[] dfRow;
        int numClusters = 0;
        while (inputTableIterator.hasNext()){
            dfRow = inputTableIterator.next();
            try {
                String label = (String)dfRow[0];
                if (label.equals("Total clusters")){
                    numClusters = (Integer)dfRow[1];
                }
                if (label.equals("Size")) {
                    int size = (Integer) dfRow[1];
                    clusterSizeList.add(size);
                    clusterFactory.startCluster();
                    while (inputTableIterator.hasNext()) {
                        dfRow = inputTableIterator.next();
                        if (dfRow[0].equals("Cluster Index")){
                            break;
                        }
                        String molecule = (String) dfRow[0];
                        int count = (Integer) dfRow[1];
                        clusterFactory.addToCluster(molecule, count);
                    }
                    clusterCompList.add(clusterFactory.returnedVerifiedCluster(size));
                }
            }
            catch (ClassCastException cce){
                throw new ClassCastException(cce.getMessage()
                        + "\nData does not match expected format of String,Integer"
                        + "\nFile: " + inpath);
            }
            catch (IllegalArgumentException iae){ //from Cluster.returnVerifiedCluster
                throw new IllegalArgumentException(iae.getMessage() + "\nFile: " + inpath, iae);
            }
            catch (NoSuchElementException nsee){ //.next()
                throw new NoSuchElementException(nsee.getMessage() + "\nFile: " + inpath);
            }
        }

        //clusterSizeListWithMonomer
        List<Integer> monomerList = new ArrayList<>();
        for (int i = 0; i < numClusters - clusterSizeList.size(); i++){
            monomerList.add(1);
        }
        clusterSizeListWithMonomer = new ArrayList<>(clusterSizeList);
        clusterSizeListWithMonomer.addAll(monomerList);
    }

    public TPStats_OLD calculateClusterStats(){
        TPStats_OLD tpStats = new TPStats_OLD();

        //Acs
        int totNumMolecules = 0;
        for (int moles: clusterSizeListWithMonomer){
            totNumMolecules += moles;
        }
        tpStats.acs = (double) totNumMolecules/ clusterSizeListWithMonomer.size();

        //fotm
        SortedMap<Integer,Integer> clusterSizeCountMap = countList(clusterSizeListWithMonomer);
        SortedMap<Integer,Double> fotm = new TreeMap<>();
        int size,count;
        for (Map.Entry<Integer,Integer> entry: clusterSizeCountMap.entrySet()){
            size = entry.getKey(); count = entry.getValue();
            fotm.put(size, (double)size*count/totNumMolecules);
        }
        tpStats.sizeFotmMap = fotm;

        //ACO
        double fraction;
        double aco = 0;
        for (Map.Entry<Integer,Double> entry: fotm.entrySet()){
            size = entry.getKey(); fraction = entry.getValue();
            aco += size * fraction;
        }
        tpStats.aco = aco;

        return tpStats;
    }

    private <T extends Comparable<T>> SortedMap<T, Integer> countList(List<T> list){
        SortedMap<T, Integer> countMap = new TreeMap<>();
        for (T e: list){
            countMap.put(e, (countMap.containsKey(e)) ? countMap.get(e) + 1 : 1);
        }
        return countMap;
    }


    public List<Integer> getClusterSizeList(){
        return (withMonomer) ? clusterSizeListWithMonomer : clusterSizeList;
    }

    public List<Cluster_OLD> getClusterCompList(){
        return clusterCompList;
    }
}
