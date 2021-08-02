package org.springsalad.clusteranalysis;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.IOException;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;

class ClusterAveragesDisplayManager implements DataDisplayManager {
	private JPanel mainPanel;
	private ClusterAveragesTableModel clusterAveragesTableModel;
	
	ClusterAveragesDisplayManager(String dataFolder,
									int firstRunNum, int lastRunNumInclusive,
									double firstTimePoint, double lastTimePointInclusive, double timeStep) {
		int numOfTP = (int)((lastTimePointInclusive - firstTimePoint)/timeStep) + 1;
		
		Path clusterAveragesFolder = Paths.get(dataFolder, DataDestination.AvgFolderEXTENSION);

		// read and store data		
		NavigableMap<String, DataFrame> dataFrameMap = new TreeMap<>();
		
		// FIXME handle exceptions properly, throw them upwards, display them to user
		Path meanFilePath = Paths.get(clusterAveragesFolder.toString(), ClusterStatsProducer.MEAN_RUN_STR + "_" + DataDestination.averagesFileName);
		try {
			DataFrame tmp = CSVHandler.readCSV(meanFilePath, 0);
			if (tmp.getSeries(0).size() == numOfTP) {
				dataFrameMap.put(ClusterStatsProducer.MEAN_RUN_STR, tmp);
			}
			else {
				throw new IOException("File does not have the number of timepoints specified in the sim file: \n" + meanFilePath);
			}
		}
		catch (IOException | IllegalArgumentException e) { //notify user
			dataFrameMap.put(ClusterStatsProducer.MEAN_RUN_STR, null);
		}
		
		Path overallFilePath = Paths.get(clusterAveragesFolder.toString(), ClusterStatsProducer.OVERALL_RUN_STR + "_" + DataDestination.averagesFileName);
		try {
			DataFrame tmp = CSVHandler.readCSV(overallFilePath, 0);
			if (tmp.getSeries(0).size() == numOfTP) {
				dataFrameMap.put(ClusterStatsProducer.OVERALL_RUN_STR, tmp);
			}
			else {
				throw new IOException("File does not have the number of timepoints specified in the sim file: \n" + overallFilePath);
			}
		}
		catch (IOException | IllegalArgumentException e) {
			dataFrameMap.put(ClusterStatsProducer.OVERALL_RUN_STR, null);
		}
		
		for (int i = firstRunNum; i<= lastRunNumInclusive; i++) {
			String runStr = String.format(ClusterStatsProducer.SINGLE_RUN_STR, i);
			Path filePath = Paths.get(clusterAveragesFolder.toString(), runStr + "_" + DataDestination.averagesFileName);
			try {
				DataFrame tmp = CSVHandler.readCSV(filePath, 0);
				if (tmp.getSeries(0).size() == numOfTP) {
					dataFrameMap.put(runStr, tmp);
				}
				else {
					throw new IOException("File does not have the number of timepoints specified in the sim file: \n" + filePath);
				}

			}
			catch (IOException | IllegalArgumentException e) {
				dataFrameMap.put(runStr, null);
			}
		}
		
		
		
		// crate UI stuff
		//JLabel panelLabel = new JLabel("Cluster Averages");
		//panelLabel.setHorizontalAlignment(SwingConstants.LEFT);
		
		mainPanel = new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
		//mainPanel.add(panelLabel);
		
		JLabel infoLabel = new JLabel("Info panel");
		infoLabel.setHorizontalAlignment(SwingConstants.CENTER);
		JTextArea infoArea = new JTextArea(infoStr);
		infoArea.setLineWrap(true);
		infoArea.setWrapStyleWord(true);
		infoArea.setEditable(false);
		JScrollPane infoPane = new JScrollPane(infoArea);
		infoPane.setPreferredSize(new Dimension(300,150));
		JPanel infoPanel = new JPanel(new BorderLayout());
		infoPanel.add(infoLabel, BorderLayout.NORTH);
		infoPanel.add(infoPane, BorderLayout.CENTER);
		mainPanel.add(infoPanel);
		
		clusterAveragesTableModel = new ClusterAveragesTableModel(dataFrameMap);
		JTable jTable = new JTable(clusterAveragesTableModel);
		JScrollPane jScrollPane = new JScrollPane(jTable);
		jScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
		jScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		mainPanel.add(jScrollPane);
	}

	@Override
	public void configureList(JLabel listLabel, JList<String> list, DefaultListModel listModel) {
		listLabel.setText("Runs");
		list.setEnabled(true);
		listModel.clear();
		for (String name: clusterAveragesTableModel.getNames()) {
			listModel.addElement(name);
		}
		list.clearSelection();
		list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
	}

	@Override
	public JPanel getMainPanel() {
		return mainPanel;
	}

	@Override
	public void setNamesToShow(List<String> selectedValuesList) {
		clusterAveragesTableModel.replaceAllNamesToShow(selectedValuesList);
	}

}
