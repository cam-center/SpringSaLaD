package org.springsalad.clusteranalysis;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.SortedMap;

public class SpecialFileWriter {
    public static void writeComposition(Path outpath, String[] moleculeNames, SortedMap<Cluster_OLD,Double> compFreqMap)
            throws IOException {
        Path outpath1 = Paths.get(outpath.toString(), "Clusters_composition.txt");
        BufferedWriter bw = new BufferedWriter(new FileWriter(outpath1.toString()));

        //setting up
        bw.append("Cluster size \t " + Arrays.toString(moleculeNames) + " : frequency");
        int currentSize = 0;

        //writing content
        for (Map.Entry<Cluster_OLD,Double> entry: compFreqMap.entrySet()){
            Cluster_OLD cluster_OLD = entry.getKey(); Double freq = entry.getValue();
            if (cluster_OLD.size > currentSize){
                currentSize = cluster_OLD.size;
                bw.newLine();
                bw.newLine();
                bw.append("  " + currentSize + "\t\t");
            }
            bw.append(cluster_OLD.toString())
                    .append(" : ")
                    .append(String.format("%.2f%%",freq*100))
                    .append("\t");
        }

        try{
            bw.close();
        }
        catch(IOException ioe){

        }
    }

    public static void writeSteadyStateSpecs(Path outpath, int[] runNumbers, double[] SSTimePoints)
            throws IOException{
        Path outpath1 = Paths.get(outpath.toString(), "Sampling_stat.txt");
        BufferedWriter bw = new BufferedWriter(new FileWriter(outpath1.toString()));
        bw.append("Run numbers: ").append(Arrays.toString(runNumbers));
        bw.newLine();
        bw.newLine();
        bw.append("Steady state time points (s): ").append(Arrays.toString(SSTimePoints));
        try{
            bw.close();
        }
        catch (IOException ioe){}
    }
}
