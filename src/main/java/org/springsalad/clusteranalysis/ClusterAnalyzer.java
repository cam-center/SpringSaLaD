package org.springsalad.clusteranalysis;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class ClusterAnalyzer {
    public static void main(String[] args) throws IOException{
/*
        //simulation0 test
        String filePathStr = "C:\\Users\\imt_w\\Documents\\SpringSalad\\Clustering_tutorial_01\\Clustering_tutorial_01_SIMULATIONS\\Simulation0_SIM_FOLDER\\Simulation0_SIM.txt";
        ClusterAnalyzer clusterAnalyzer1 = new ClusterAnalyzer(filePathStr, true,
                new double[]{0.0010, 0.0020, 0.005, 0.006, 0.007, 0.008, 0.009, 0.01},new int[]{0,1,2});
        clusterAnalyzer1.getMeanTrajectory();

        clusterAnalyzer1.getSingleTrajectory(0);
        clusterAnalyzer1.getSingleTrajectory(1);
        clusterAnalyzer1.getSingleTrajectory(2);

        clusterAnalyzer1.getSteadyStateDistribution(new double[] {0.008, 0.009, 0.01});

*/
        //simulation3 test
        String filePathStr = "C:\\Users\\imt_w\\Documents\\SpringSalad\\Clustering_tutorial_01\\Clustering_tutorial_01_SIMULATIONS\\Simulation3_SIM.txt";
        double[] timepoints = new double[201];
        for (int i = 0; i <= 200; i++){
            timepoints[i] = i * 0.0001;
        }

        ClusterAnalyzer clusterAnalyzer2 = new ClusterAnalyzer(filePathStr, true,
                timepoints,new int[]{0,1,2});
        clusterAnalyzer2.getMeanTrajectory();

        clusterAnalyzer2.getSingleTrajectory(0);
        clusterAnalyzer2.getSingleTrajectory(1);
        clusterAnalyzer2.getSingleTrajectory(2);

        clusterAnalyzer2.getSteadyStateDistribution(new double[] {0.018, 0.02});
    }

    SimFile simFileObj;
    double tTotal;
    int[] sortedRunNums;

    double dt;
    boolean withMonomer;
    String[] moleculeNames;
    Double[] sortedTPValues;

    Trajectory[] trajectories;

    Path outFolder;

    boolean validSimFile = true;

    public ClusterAnalyzer(String dataFolder, int startIndex, int finishIndexInclusive) throws IOException{
        Pattern simNamePattern = Pattern.compile(".*(_SIM)_FOLDER");
        Matcher simNameMatcher = simNamePattern.matcher(dataFolder);
        if (simNameMatcher.find()){
            String simfile = dataFolder.substring(0,simNameMatcher.end()) + ".txt";
            SimFile simFileObj = new SimFile(simfile);
            double[] timeStats = simFileObj.getTimeStats();
            double tTotal = timeStats[0];
            final double dt_data = timeStats[3];
            final long numTPValues = (long) (tTotal/dt_data) + 1;
            double[] timePointValues = DoubleStream.iterate(0,a -> a+dt_data).limit(numTPValues).toArray();
            int[] runNumbers = IntStream.rangeClosed(startIndex,finishIndexInclusive).toArray();
            try {
                init(simFileObj, true, timePointValues, runNumbers);
            }
            catch(IOException ioe){
                //ExceptionDisplayer.displayException(ioe);
            }
        }
    }

    public ClusterAnalyzer(String simfile, boolean withMonomer, double[] timePointValues, int[] runNumbers)
            throws IOException{
        init(new SimFile(simfile), withMonomer, timePointValues, runNumbers);
    }

    public void init(SimFile simFileObj, boolean withMonomer, double[] timePointValues, int[] runNumbers)
            throws IOException{
        this.simFileObj = simFileObj;
        outFolder = simFileObj.getOutFolder("Cluster_stat" + ((withMonomer) ? "" : "_wom"));

        double[] timeStats = simFileObj.getTimeStats();

        this.tTotal = timeStats[0];
        this.dt = timeStats[3];
        int nf = Math.max(0,BigDecimal.valueOf(dt).stripTrailingZeros().scale());
        this.withMonomer = withMonomer;
        moleculeNames = simFileObj.getMolecules().keySet().toArray(new String[0]);

        //sort, then check
        Arrays.sort(timePointValues);
        Arrays.sort(runNumbers);
        checkTimePointValues(timePointValues, tTotal, dt);
        if (runNumbers[runNumbers.length-1] > simFileObj.getNumRuns()){
            throw new IllegalArgumentException("One or more of the given run numbers are invalid");
        }

        this.sortedTPValues = Arrays.stream(timePointValues).boxed().toArray(Double[]::new);
        this.sortedRunNums = runNumbers;

        //<editor-fold, defaultstate = "collapsed", desc = "initializing trajectories and timepoints">
        TPFactory tpFactory = new TPFactory(nf,withMonomer,moleculeNames);
        TrajFactory trajFactory = new TrajFactory(sortedTPValues,dt,tpFactory);
        Path inpath = simFileObj.getSimFolder();
        trajectories = new Trajectory[runNumbers.length];
        for (int i = 0; i < trajectories.length; i++){
            trajectories[i] = trajFactory.manufactureTraj(runNumbers[i], inpath);
        }
        //</editor-fold>

        // <editor-fold, defaultstate = "collapsed", desc = "/*generating tpv and runs (not used now)*/">
        /*
        // check this: works like in the python script but does not make sense
        // unless tTotal and dt are both whole numbers
        // actually the code below does not work if dt divides tTotal
        timePointValues = new double[(int)(this.tTotal/dt) +2];
        for (int i = 0; i*dt<this.tTotal+dt; i++){
            timePointValues[i] = i*dt;
        }

        this.runNumbers = new int[runs];
        for (int i = 0; i<runs; i++){
            this.runNumbers[i] = i;
        }
        */
        //</editor-fold>
    }

    //did not use dt to deal with tps that are not int multiples of dt
    private static void checkTimePointValues(double[] timePointValues, double tTotal, double dt) {
        if (timePointValues[timePointValues.length-1] > tTotal){
            throw new IllegalArgumentException("One or more of the given time points exceed the total simulation time");
        }
    }


    public void calculateMeanAndSingleTrajectories() throws IOException{//very inefficient now
        getMeanTrajectory();
        for (int runNum: sortedRunNums){
            getSingleTrajectory(runNum);
        }
    }

    public void getMeanTrajectory() throws IOException{
        TrajStats trajStats;
        List<List<Double>> acsListList = new ArrayList<>();
        List<List<Double>> acoListList = new ArrayList<>();

        for (Trajectory trajectory: trajectories){
            trajStats = trajectory.gatherClusterStats();
            acsListList.add(trajStats.acsList);
            acoListList.add(trajStats.acoList);
        }

        List<Double> meanAcsList = tableMean(acsListList,true);
        List<Double> meanAcoList = tableMean(acoListList,true);

        //write to file
        /*List<Double> timesInMS = new ArrayList<>();
        for (int i = 0; i< sortedTPValues.length; i++){
            timesInMS.add(sortedTPValues[i] * 1000);
        }*/

        Path outfilePath = Paths.get(outFolder.toString(), "Clustering_dynamics.csv");
        try {
            CSVHandler.writeCSV(outfilePath,
                    new String[]{"Time (s)", "ACS", "ACO"}, Arrays.asList(sortedTPValues), meanAcsList, meanAcoList);
        }
        catch (IOException ioe){
            ExceptionDisplayer.justDisplayException("Cannot write to file: " + outfilePath.toString(), ioe);
        }
    }

    // not so sure about how to use wild cards here
    private static List<Double> tableMean(List<List<Double>> table, boolean useOuterAxis){
        List<Double> meanList = new ArrayList<>();
        if (useOuterAxis){
            for (int col = 0; col < table.get(0).size(); col++){
                double sum = 0;
                for (int row = 0; row < table.size(); row++){
                    sum += table.get(row).get(col);
                }
                meanList.add(sum/table.size());
            }
        }
        else {
            for (int row = 0; row < table.size(); row++){
                double sum = 0;
                for (int col = 0; col < table.get(0).size(); col++){
                    sum += table.get(row).get(col);
                }
                meanList.add(sum/table.get(0).size());
            }
        }
        return meanList;
    }

    public void getSingleTrajectory(int trajNum) throws IOException{
        //checking for existence
        int j = Arrays.binarySearch(sortedRunNums, trajNum);
        if (j<0) {
            throw new IllegalArgumentException(String.format("Trajectory %d does not exist in the current analyzer",
                    trajNum));
        }

        //calculations
        TrajStats trajStats = trajectories[j].gatherClusterStats();

        //write to file
        /*List<Double> timesInMS = new ArrayList<>();
        for (int i = 0; i< sortedTPValues.length; i++){
            timesInMS.add(sortedTPValues[i] * 1000);
        }*/
        Path averagesOutpath = Paths.get(outFolder.toString(), String.format("Run%d_clustering_dynamics.csv", trajNum));
        Path fractionsOutpath = Paths.get(outFolder.toString(), String.format("Run%d_distribution_dynamics.csv", trajNum));
        try {
            CSVHandler.writeCSV(averagesOutpath,
                    new String[]{"Time (s)", "ACS", "ACO"}, Arrays.asList(sortedTPValues), trajStats.acsList, trajStats.acoList);
        }
        catch (IOException ioe){
            ExceptionDisplayer.justDisplayException("Cannot write to file: " + averagesOutpath.toString(), ioe);
        }
        try {
            CSVHandler.writeCSV(fractionsOutpath,
                    new String[]{"Time (s)", "foTM"}, Arrays.asList(sortedTPValues), trajStats.fotmList);
        }
        catch (IOException ioe){
            ExceptionDisplayer.justDisplayException("Cannot write to file: " + fractionsOutpath.toString(), ioe);
        }
    }



    public void getSteadyStateDistribution(double[] SSTimePoints) throws IOException{
        try {
            checkSSTimePoints(SSTimePoints);
        }
        catch (IllegalArgumentException iae){
            throw new IllegalArgumentException(iae.getMessage());
        }

        //calculate distribution
        SortedMap<Integer,Integer> sizeCountMap = createSizeCountMap(SSTimePoints);

        final double totalCount = sizeCountMap.values().stream().reduce(0, Integer::sum);

        List<Double> sizeFreqList = sizeCountMap.values()
                                                .stream()
                                                .map((Integer a) -> a/totalCount)
                                                .collect(toList());

        final double totalMols = sizeCountMap.entrySet().stream()
                .map((a)->a.getKey()*a.getValue())
                .reduce(0,Integer::sum);

        List<Double> sizeFotmList = sizeCountMap.entrySet().stream()
                .map((a)->a.getKey()*a.getValue()/totalMols)
                .collect(toList());


        //calculate composition (using some of the results from above)
        SortedMap<Cluster,Integer> compCountMap = createCompCountMap(SSTimePoints);

        SortedMap<Cluster, Double> compFreqMap = compCountMap.entrySet().stream()
                .collect(toMap(Map.Entry::getKey,
                        (a) -> (double)a.getValue()/sizeCountMap.get(a.getKey().size),
                        (b,c) -> b,
                        TreeMap::new));


        //write distribution
        CSVHandler.writeCSV(Paths.get(outFolder.toString(),"SteadyState_distribution.csv"),
                new String[]{"Cluster size", "frequency", "foTM"},
                new ArrayList<>(sizeCountMap.keySet()), sizeFreqList, sizeFotmList);

        //write composition
        SpecialFileWriter.writeComposition(outFolder, moleculeNames, compFreqMap);

        //write steady state specs
        //double[] timeInMS = Arrays.stream(SSTimePoints).map((d) -> d*1000).toArray();
        SpecialFileWriter.writeSteadyStateSpecs(outFolder, sortedRunNums, SSTimePoints);
    }

    // not too sure about testing equality with doubles
    private void checkSSTimePoints(double[] SSTimePoints) throws IllegalArgumentException{
        for (double sstp: SSTimePoints){
            if (Arrays.binarySearch(sortedTPValues, sstp,
                            (a,b) -> (a-b>dt*1E-10) ? 1 : (b-a>dt*1E-10)?-1:0) // Double comparator with threshold
             < 0){ //not found
                throw new IllegalArgumentException("One of the given steady state time points is invalid");
            }
        }
    }

    private SortedMap<Integer,Integer> createSizeCountMap(double[] SSTimePoints){
        SortedMap<Integer,Integer> sizeCountMap = new TreeMap<>();
        for (Trajectory traj: trajectories){
            List<Integer> clusterSizeList = traj.gatherClusterSizeList(SSTimePoints);
            for (Integer size: clusterSizeList){
                sizeCountMap.put(size, sizeCountMap.containsKey(size) ? sizeCountMap.get(size)+1 : 1);
            }
        }
        return sizeCountMap;
    }

    private SortedMap<Cluster,Integer> createCompCountMap(double[] SSTimePoints){
        SortedMap<Cluster,Integer> compCountMap = new TreeMap<>();
        for (Trajectory traj: trajectories){
            List<Cluster> clusterCompList = traj.gatherClusterCompList(SSTimePoints);
            for (Cluster cluster: clusterCompList){
                compCountMap.put(cluster, compCountMap.containsKey(cluster) ? compCountMap.get(cluster)+1 : 1);
            }
        }
        return compCountMap;
    }

}
