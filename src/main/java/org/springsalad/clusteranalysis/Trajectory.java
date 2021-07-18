package org.springsalad.clusteranalysis;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Trajectory {
    /*
    public static void main(String[] args){
        TimePoint.moleculesNames = new String[]{"Nck", "NWASP", "nephrin"};
        Cluster.molecules = TimePoint.moleculesNames;
        TimePoint.nf = 4;
        Trajectory.timePointValues = new Double[]{0.0010, 0.0020, 0.0100};
        Path inpath = Paths.get("C:\\Users\\imt_w\\Documents\\SpringSalad\\Clustering_tutorial_01\\Clustering_tutorial_01_SIMULATIONS\\Simulation0_SIM_FOLDER");
        Trajectory trajectory = new Trajectory(0,inpath);
        System.out.println(trajectory.gatherClusterStats());
    }
     */

    //<editor-fold, defaultstate = "collapsed", desc = "supposedly 'private' constructor and builder setters">
    int trajNum;
    Path inpath;
    Trajectory (int trajNum, Path inpath){
        this.trajNum = trajNum;
        this.inpath = Paths.get(inpath.toString(),"data", "Run"+trajNum);
    }
    double dt;
    public Trajectory setDt(double dt){
        this.dt = dt;
        return this;
    }
    Double[] timePointValues;
    public Trajectory setTPV (Double[] timePointValues){
        this.timePointValues = timePointValues;
        return this;
    }
    TimePoint[] timePoints;
    Trajectory initializeTPs(TPFactory tpFactory) throws IOException {
        timePoints = new TimePoint[timePointValues.length];
        for (int i = 0; i < timePoints.length; i++){
            timePoints[i] = tpFactory.manufactureTP(timePointValues[i], inpath);
        }
        return this;
    }
    //</editor-fold>


    public TrajStats gatherClusterStats(){
        TrajStats trajStats = new TrajStats();
        TpStats tpStats;
        for (TimePoint timePoint: timePoints){
            tpStats = timePoint.calculateClusterStats();
            trajStats.acsList.add(tpStats.acs);
            trajStats.acoList.add(tpStats.aco);
            trajStats.fotmList.add(tpStats.foTM);
        }
        return trajStats;
    }

    public List<Integer> gatherClusterSizeList(double[] SSTimePoints){
        List<Integer> clusterSizeList = new ArrayList<>();
        for (double sstp: SSTimePoints){
            int tpIndex = Arrays.binarySearch(timePointValues, sstp,(a,b) -> (a-b>dt*1E-10) ? 1 : (b-a>dt*1E-10)?-1:0);
            clusterSizeList.addAll(timePoints[tpIndex].getClusterSizeList());
        }
        return clusterSizeList;
    }

    public List<Cluster> gatherClusterCompList(double[] SSTimePoints){
        List<Cluster> clusterCompList = new ArrayList<>();
        for (double sstp: SSTimePoints) {
            int tpIndex = Arrays.binarySearch(timePointValues, sstp,(a,b) -> (a-b>dt*1E-10) ? 1 : (b-a>dt*1E-10)?-1:0);
            clusterCompList.addAll(timePoints[tpIndex].getClusterCompList());
        }
        return clusterCompList;
    }



}
