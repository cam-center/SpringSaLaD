package org.springsalad.clusteranalysis;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.SortedMap;
import java.util.Map;

public class SpecialFileWriter {
    public static void writeComposition(Path outpath, String[] moleculeNames, SortedMap<Cluster,Double> compFreqMap)
            throws IOException {
        Path outpath1 = Paths.get(outpath.toString(), "Clusters_composition.txt");
        BufferedWriter bw = new BufferedWriter(new FileWriter(outpath1.toString()));

        //setting up
        bw.append("Cluster size \t " + Arrays.toString(moleculeNames) + " : frequency");
        int currentSize = 0;

        //writing content
        for (Map.Entry<Cluster,Double> entry: compFreqMap.entrySet()){
            Cluster cluster = entry.getKey(); Double freq = entry.getValue();
            if (cluster.size > currentSize){
                currentSize = cluster.size;
                bw.newLine();
                bw.newLine();
                bw.append("  " + currentSize + "\t\t");
            }
            bw.append(cluster.toString())
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
