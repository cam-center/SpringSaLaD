package org.springsalad.clusteranalysis;

import java.io.IOException;
import java.nio.file.Path;

public class TPFactory {
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
