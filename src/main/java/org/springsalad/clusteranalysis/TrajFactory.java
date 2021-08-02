package org.springsalad.clusteranalysis;

import java.io.IOException;
import java.nio.file.Path;

public class TrajFactory {
    Double[] timePointValues; double dt; TPFactory tpFactory;
    public TrajFactory(Double[] timePointValues, double dt, TPFactory tpFactory){
        this.dt = dt;
        this.timePointValues = timePointValues;
        this.tpFactory = tpFactory;
    }

    public Trajectory_OLD manufactureTraj(int trajNum, Path inpath) throws IOException {
        return new Trajectory_OLD(trajNum,inpath).setTPV(timePointValues).setDt(dt).initializeTPs(tpFactory);
    }
}
