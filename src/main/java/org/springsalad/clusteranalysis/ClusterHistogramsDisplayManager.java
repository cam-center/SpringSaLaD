package org.springsalad.clusteranalysis;



import static java.util.stream.Collectors.toList;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;


class ClusterHistogramsDisplayManager implements DataDisplayManager {
	private ClusterHistogramsTableModel sizeFreqFotmTableModel;
	private ClusterHistogramsTableModel sizeCompFreqTableModel;
	private JComboBox<String> runComboBox, timeComboBox;
	private JPanel mainPanel;
	
	private String selectedRun, selectedTime;
	
	ClusterHistogramsDisplayManager(String dataFolder, 
									int firstRunNum, int lastRunNumInclusive,
									double firstTimePoint, double lastTimePointInclusive, double timeStep) {	
		// getting data
		long numRuns = lastRunNumInclusive-firstRunNum +1;
		
		List<String> runStrList = new ArrayList<>();
		runStrList.add(ClusterStatsProducer.MEAN_RUN_STR);
		runStrList.add(ClusterStatsProducer.OVERALL_RUN_STR);
		IntStream.iterate(firstRunNum, i -> i+1).limit(numRuns).mapToObj(i -> String.format(ClusterStatsProducer.SINGLE_RUN_STR, i)).forEach(runStrList::add);
		long numTPVs = (long)((lastTimePointInclusive - firstTimePoint)/timeStep) +1;
		int nf = Math.max(0,BigDecimal.valueOf(timeStep).stripTrailingZeros().scale());
		List<String> tpvStrList = DoubleStream.iterate(firstTimePoint, d -> d+timeStep).limit(numTPVs).mapToObj(tpv -> String.format("%."+nf+"f",tpv)).collect(toList());
		
		Path sffFolder = Paths.get(dataFolder, DataDestination.SffFolderEXTENSION);
		sizeFreqFotmTableModel = new ClusterHistogramsTableModel(getData(sffFolder, DataDestination.sizeFreqFotmFileName, runStrList, tpvStrList));
		Path scfFolder = Paths.get(dataFolder, DataDestination.ScfFolderEXTENSION);
		sizeCompFreqTableModel = new ClusterHistogramsTableModel(getData(scfFolder, DataDestination.sizeCompFreqFileName, runStrList, tpvStrList));
		
		// UI init		
		//JLabel mainLabel = new JLabel("Cluster Histograms");
		//mainLabel.setHorizontalAlignment(SwingConstants.CENTER);
		
		JLabel sffLabel = new JLabel("Cluster Size Frequency and Fraction of total molecules");
		sffLabel.setHorizontalAlignment(SwingConstants.CENTER);
		
		JTable jTable1 = new JTable(sizeFreqFotmTableModel);
		jTable1.setFillsViewportHeight(true);
		JScrollPane jScrollPane1 = new JScrollPane(jTable1);
		jScrollPane1.setPreferredSize(new Dimension(350,150));
		jScrollPane1.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		
		JLabel scfLabel = new JLabel("Frequency of Cluster Compositions");
		scfLabel.setHorizontalAlignment(SwingConstants.CENTER);
		
		JTable jTable2 = new JTable(sizeCompFreqTableModel);
		jTable2.setFillsViewportHeight(true);
		JScrollPane jScrollPane2 = new JScrollPane(jTable2);
		jScrollPane2.setPreferredSize(new Dimension(350,150));
		jScrollPane2.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
		
		JPanel tablePanel = new JPanel();
		tablePanel.setLayout(new BoxLayout(tablePanel, BoxLayout.Y_AXIS));
		tablePanel.add(sffLabel);
		tablePanel.add(jScrollPane1);
		tablePanel.add(scfLabel);
		tablePanel.add(jScrollPane2);
		JScrollPane mainScrollPane = new JScrollPane(tablePanel);
		
		
		JLabel runCBLabel = new JLabel("Run: ");
		runComboBox = new JComboBox<>(sizeFreqFotmTableModel.getRunStrSet().toArray(new String[0]));
		JLabel timeCBLabel = new JLabel("Time: ");
		timeComboBox = new JComboBox<>(sizeFreqFotmTableModel.getTimeStrSet().toArray(new String[0]));
		ItemListener cBListener = new ComboBoxListener();
		runComboBox.addItemListener(cBListener);
		timeComboBox.addItemListener(cBListener);
		JPanel comboBoxesPanel = new JPanel();
		comboBoxesPanel.add(runCBLabel);
		comboBoxesPanel.add(runComboBox);
		comboBoxesPanel.add(timeCBLabel);
		comboBoxesPanel.add(timeComboBox);
		
		JLabel infoLabel = new JLabel("Info panel");
		infoLabel.setHorizontalAlignment(SwingConstants.CENTER);
		JTextArea infoArea = new JTextArea(infoStr);
		infoArea.setLineWrap(true);
		infoArea.setWrapStyleWord(true);
		infoArea.setEditable(false);
		JScrollPane infoPane = new JScrollPane(infoArea);
		infoPane.setPreferredSize(new Dimension(300,100));
		JPanel infoPanel = new JPanel(new BorderLayout());
		infoPanel.add(infoLabel, BorderLayout.NORTH);
		infoPanel.add(infoPane, BorderLayout.CENTER);
		
		mainPanel = new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
		//mainPanel.add(mainLabel);
		mainPanel.add(infoPanel);
		mainPanel.add(comboBoxesPanel);
		mainPanel.add(mainScrollPane);
	}
	
	private SortedMap<String, SortedMap<String, DataFrame>> getData(Path dataGridFolder, String fileNameEnding,
																	List<String> runStrList, List<String> tpvStrList){
		SortedMap<String, SortedMap<String, DataFrame>> dataFrameGrid = new TreeMap<>();
		for (String runStr: runStrList) {
			String runFolderStr = dataGridFolder.toString() + "/" + runStr;
			SortedMap<String, DataFrame> innerMap = new TreeMap<>();
			for (String tpvStr: tpvStrList) {
				Path filePath = Paths.get(runFolderStr, runStr + "_" + tpvStr + "_" + fileNameEnding);
				// FIXME check if i handled this exception properly
				try {
					DataFrame df = CSVHandler.readCSV(filePath, 0);
					innerMap.put(tpvStr, df);
				}
				catch (IOException | IllegalArgumentException e) {
					innerMap.put(tpvStr, null);
				}
			}
			dataFrameGrid.put(runStr, innerMap);
		}
		return dataFrameGrid;
	}
	

	@Override
	public void configureList(JLabel listLabel, JList<String> list, DefaultListModel listModel) {
		listLabel.setText("");
		list.setEnabled(false);
		listModel.clear();
	}

	@Override
	public JPanel getMainPanel() {
		return mainPanel;
	}

	@Override
	public void setNamesToShow(List<String> selectedValuesList) {}
	
	private class ComboBoxListener implements ItemListener{
		@Override
		public void itemStateChanged(ItemEvent itemEvent) {
			sizeFreqFotmTableModel.setDFToShow((String)runComboBox.getSelectedItem(), (String)timeComboBox.getSelectedItem());
			sizeCompFreqTableModel.setDFToShow((String)runComboBox.getSelectedItem(), (String)timeComboBox.getSelectedItem());
		}
	}

}
