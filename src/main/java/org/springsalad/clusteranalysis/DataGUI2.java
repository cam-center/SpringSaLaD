package org.springsalad.clusteranalysis;

import java.awt.event.ItemEvent;
import java.io.File;
import java.nio.file.Paths;

import javax.swing.event.ListSelectionEvent;

import org.springsalad.dataprocessor.DataGUI;
import org.springsalad.langevinsetup.SystemTimes;
import org.springsalad.runlauncher.Simulation;

public class DataGUI2 extends DataGUI{
	private TableClusterPanel tableClusterPanel;
	private static final String CLUSTER_STATS2 = "CLUSTER STATS 2";
	
	private DataDisplayManager currentDataDisplayManager;
	private ClusterAveragesDisplayManager clusterAveragesDisplayManager;
	private ClusterHistogramsDisplayManager clusterHistogramsDisplayManager;
	
	public static void main(String[] args) {
		Simulation sim = new Simulation(new File("C:\\Users\\imt_w\\Documents\\SpringSalad\\Clustering_tutorial_01\\Clustering_tutorial_01_SIMULATIONS\\Simulation3_SIM.txt"));
		new DataGUI2("Test", sim);
	}
	
	public DataGUI2 (String title, Simulation simulation) {
		super(title, simulation);
		// FIXME check if trackClusters is true first
		SystemTimes systemTimes = this.simulation.getSystemTimes();
		int numRuns = this.simulation.getRunNumber();
		clusterAveragesDisplayManager = new ClusterAveragesDisplayManager(processor.getDataFolder(), 
																			0, numRuns-1, 
																			0, systemTimes.getTotalTime(), systemTimes.getdtdata());
		clusterHistogramsDisplayManager = new ClusterHistogramsDisplayManager(processor.getDataFolder(), 
																				0, numRuns-1, 
																				0, systemTimes.getTotalTime(), systemTimes.getdtdata());
		dataClassBox.addItem(CLUSTER_STATS2);
	}
	
	@Override
	public void itemStateChanged(ItemEvent event) {
		super.itemStateChanged(event);
		if (event.getStateChange() == ItemEvent.SELECTED) {
			if (dataClassBox.getSelectedItem().equals(CLUSTER_STATS2)) {
				Object source = event.getSource();
				if (source == dataClassBox) {
					currentPick = CLUSTER_STATS2;
					if (dataTypeBox.getItemCount()==3) {
						if (dataTypeBox.getSelectedIndex()==2) {
							dataTypeBox.setSelectedIndex(1);
						}
						dataTypeBox.removeItemAt(2);
					}
				}
				if (dataTypeBox.getSelectedItem().equals(dataTypes[0])) {
					currentDataDisplayManager = clusterAveragesDisplayManager;
				}
				else {
					currentDataDisplayManager = clusterHistogramsDisplayManager;
				}
				currentDataDisplayManager.configureList(listLabel, list, listModel);
				centerPanel.removeAll();
				centerPanel.add(currentDataDisplayManager.getMainPanel());
				centerPanel.validate();
				centerPanel.repaint();
			}
			else {
				currentDataDisplayManager = null;
			}
		}
	}
	
	@Override
	public void valueChanged(ListSelectionEvent event) {
		super.valueChanged(event);
		if (!event.getValueIsAdjusting()) {
			if (currentPick.equals(CLUSTER_STATS2)) {
				currentDataDisplayManager.setNamesToShow(list.getSelectedValuesList());
			}
		}
	}
}
