/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package runlauncher;

import helpersetup.Constraints;
import helpersetup.Fonts;
import java.awt.BorderLayout;
import javax.swing.*;
import langevinsetup.Global;
import langevinsetup.ValueCellEditor;
import langevinsetup.ValueTextField;

public class EditInitialConditionsTablePanel extends JPanel {
    
    public EditInitialConditionsTablePanel(Global g, Simulation simulation){
        this.setLayout(new BorderLayout());
        
        JTable table = new JTable(new EditInitialConditionsTableModel(g, simulation));
        table.setFillsViewportHeight(true);
        table.setDefaultEditor(Double.class, new ValueCellEditor(ValueTextField.DOUBLE, Constraints.POSITIVE, true));
        table.setDefaultEditor(Integer.class, new ValueCellEditor(ValueTextField.INTEGER, Constraints.NONNEGATIVE, true));
        
        JScrollPane scrollPane = new JScrollPane(table);
        
        JLabel label = new JLabel("Initial Conditions", JLabel.CENTER);
        label.setFont(Fonts.TITLEFONT);
        
        this.add(label, "North");
        
        this.add(scrollPane,"Center");
    }
    
}
