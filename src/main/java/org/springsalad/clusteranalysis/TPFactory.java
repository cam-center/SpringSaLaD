package org.springsalad.clusteranalysis;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TPFactory {
	public static void main(String[] args) throws IOException {
		TPFactory tpFactory = new TPFactory(4, true, new String[] {"Nck", "NWASP", "nephrin"});
		TimePoint tp = tpFactory.manufactureTP(0.02, Paths.get("C:\\Users\\imt_w\\Documents\\SpringSalad\\Clustering_tutorial_01\\Clustering_tutorial_01_SIMULATIONS\\Simulation3_SIM_FOLDER\\data\\Run0"));
		System.out.println(tp.clusterSizeListWithMonomer);
		System.out.println(tp.clusterCompList);
	}
	
    int nf; boolean withMonomer; String[] moleculeNames;
    public TPFactory(int nf, boolean withMonomer, String[] moleculeNames){
        this.nf = nf;
        this.withMonomer = withMonomer;
        this.moleculeNames = moleculeNames;
    }

    public TimePoint manufactureTP(double timePointValue, Path inpath) throws IOException {
        return new TimePoint(timePointValue,inpath)
                .setNf(nf)
                .setWithMonomer(withMonomer)
                .setMoleculeNames(moleculeNames)
                .finishInitializing();
    }
}
