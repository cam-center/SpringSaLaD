/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package runlauncher;

import helpersetup.Constraints;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import javax.swing.JTable;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import langevinsetup.ValueCellEditor;
import langevinsetup.ValueTextField;

public class SimulationTablePanel extends JPanel {
    
    private final SimulationTableModel tableModel;
    private final JTable table;
    
    public SimulationTablePanel(SimulationManager sm){
        this.setLayout(new BorderLayout());
        tableModel = new SimulationTableModel(sm);
        table = new JTable(tableModel);
        table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.setDefaultEditor(Integer.class, new ValueCellEditor(ValueTextField.INTEGER, Constraints.POSITIVE, false));
        JScrollPane scrollPane = new JScrollPane(table);
        
        this.add(scrollPane, "Center");
    }
    
    public void updateTable(){
        tableModel.fireTableDataChanged();
    }
    
    public int getSelectedRow(){
        return table.getSelectedRow();
    }
    
}
