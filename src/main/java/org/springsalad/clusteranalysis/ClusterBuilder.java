package org.springsalad.clusteranalysis;

import java.util.ArrayList;
import java.util.List;

class ClusterBuilder {
	
	private final int supposedSize;
	private final List<String> molNames;
	private final List<Integer> composition;
	public ClusterBuilder(List<String> molNames, int supposedSize) {
		this.supposedSize = supposedSize;
		this.molNames = molNames;
		this.composition = new ArrayList<>(molNames.size());
	}

	public void addToCluster(String molecule, int count) {
		int index = molNames.indexOf(molecule);
		if (index == -1) {
			throw new IllegalArgumentException("Cluster builder received an invalid molecule name: " + molecule);
		}
		composition.add(index, count);		
	}

	public Cluster getCluster() {
		Cluster c = new Cluster(molNames, composition);
		if (c.size != supposedSize) {
			throw new IllegalStateException("A cluster of the specified size cannot be constructed from the given composition");
		}
		return c;
	}

}
