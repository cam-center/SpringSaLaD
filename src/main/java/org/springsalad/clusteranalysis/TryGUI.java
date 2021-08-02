package org.springsalad.clusteranalysis;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.nio.file.Path;
import java.nio.file.Paths;

public class TryGUI implements ItemListener, ListSelectionListener {
    public static void main(String[] args) throws Exception{
        new TryGUI();
    }

    private JPanel swappedPanel;
    private JComboBox dataClassBox;
    private JComboBox dataTypeBox;
    private static String CLUSTERS2 = "CLUSTERS2";
    private JScrollPane jScrollPane;
    private DefaultListModel<String> listModel;
    private JList<String>  jList;
    private JPanel centerPanel;

    public TryGUI()
            throws ClassNotFoundException, UnsupportedLookAndFeelException, InstantiationException, IllegalAccessException {
        System.out.println(UIManager.getSystemLookAndFeelClassName());
        System.out.println(UIManager.getLookAndFeel());
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

        JLabel dataClassLabel = new JLabel("Data Class");
        String [] dataClasses = {"MOLECULE COUNTS", "BOND DATA",
                "STATE DATA", "SITE DATA", "CLUSTER DATA", "RUNNING TIMES"};
        dataClassBox = new JComboBox(dataClasses);
        dataClassBox.addItem(CLUSTERS2);
        dataClassBox.addItemListener(this);

        JLabel dataTypeLabel = new JLabel("Data Class");
        String [] dataTypes = {"AVERAGE", "HISTOGRAM", "RAW DATA"};
        dataTypeBox = new JComboBox(dataTypes);

        JPanel topPanel = new JPanel();
        topPanel.add(dataClassLabel);
        topPanel.add(dataClassBox);
        topPanel.add(dataTypeLabel);
        topPanel.add(dataTypeBox);


        final int ROWS = 3, COLUMNS = 5;
        JTable jTable = new JTable(ROWS,COLUMNS);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLUMNS; c++) {
                jTable.getModel().setValueAt(r*COLUMNS+c,r,c);
            }
        }
        Font newFont = jTable.getFont().deriveFont(38f);
        jTable.setFont(newFont);
        jTable.setRowHeight(jTable.getFontMetrics(newFont).getHeight());

        jScrollPane = new JScrollPane();
        jScrollPane.setViewportView(jTable);

        //the two lines that need to be add to init of DataGUI2
        Path dataFolder = Paths.get("C:\\Users\\imt_w\\Documents\\SpringSalad\\Clustering_tutorial_01\\Clustering_tutorial_01_SIMULATIONS\\Simulation3_SIM_FOLDER\\data");
        swappedPanel = new TableClusterPanel(dataFolder);


        centerPanel = new JPanel();
        centerPanel.add(jScrollPane);
        
        JLabel jListLabel = new JLabel("JList");
        listModel = new DefaultListModel<>();
        listModel.addElement("Option 1");
        jList = new JList<>(listModel);
        JScrollPane pane = new JScrollPane(jList);
        pane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        pane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.add(jListLabel, "North");
        leftPanel.add(pane, "Center");

        JFrame jFrame = new JFrame("test");
        jFrame.add(centerPanel,"Center");
        jFrame.add(topPanel,"North");
        jFrame.add(leftPanel, "West");

        jFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        jFrame.pack();
        jFrame.setVisible(true);
    }

    @Override
    public void itemStateChanged(ItemEvent event){
        if (event.getStateChange()==ItemEvent.SELECTED){
            Object source = event.getSource();
            if (source == dataClassBox){
                if (dataClassBox.getSelectedItem().equals(CLUSTERS2)){
                    centerPanel.removeAll();
                    centerPanel.add(swappedPanel);
                }
                else{
                    centerPanel.removeAll();
                    centerPanel.add(jScrollPane);
                }
                centerPanel.validate();
                centerPanel.repaint();
            }
        }
    }
    
    public void valueChanged(ListSelectionEvent event) {
    	
    }
}
