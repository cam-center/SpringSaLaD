package org.springsalad.clusteranalysis;

import java.awt.event.ItemEvent;
import java.nio.file.Paths;

import javax.swing.event.ListSelectionEvent;

import org.springsalad.dataprocessor.DataGUI;
import org.springsalad.runlauncher.Simulation;

public class DataGUI2 extends DataGUI{
	private TableClusterPanel tableClusterPanel;
	private static final String CLUSTER_STATS2 = "CLUSTER STATS 2";
	
	public DataGUI2 (String title, Simulation simulation) {
		super(title, simulation);
		tableClusterPanel = new TableClusterPanel(Paths.get(processor.getDataFolder()));
		dataClassBox.addItem(CLUSTER_STATS2);
	}
	
	@Override
	public void itemStateChanged(ItemEvent event) {
		super.itemStateChanged(event);
		if (event.getStateChange() == ItemEvent.SELECTED) {
			Object source = event.getSource();
			if (source == dataClassBox) {
				if (dataClassBox.getSelectedItem().equals(CLUSTER_STATS2)) {
					currentPick = CLUSTER_STATS2;
					listLabel.setText(currentPick);
					dataTypeBox.setEnabled(false);
					dataTypeBox.setSelectedItem("AVERAGE");
					
					//update j list
					listModel.clear();
					for (String string: tableClusterPanel.getTableNames()) {
						listModel.addElement(string);
					}
					list.clearSelection();
					list.setEnabled(true);
					
					//swap panels
					centerPanel.removeAll();
					centerPanel.add(tableClusterPanel);
					centerPanel.validate();
					centerPanel.repaint();
				}
			}
		}
	}
	
	@Override
	public void valueChanged(ListSelectionEvent event) {
		super.valueChanged(event);
		if (!event.getValueIsAdjusting()) {
			if (dataTypeBox.getSelectedIndex() == 0 && currentPick.equals(CLUSTER_STATS2)) {
				tableClusterPanel.replaceAllNamesToShow(list.getSelectedValuesList());
			}
		}
	}
}
