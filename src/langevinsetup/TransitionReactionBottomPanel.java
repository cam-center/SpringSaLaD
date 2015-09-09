/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package langevinsetup;

import helpersetup.Constraints;
import helpersetup.Fonts;
import java.awt.Dimension;
import java.awt.BorderLayout;
import javax.swing.JTable;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JScrollPane;

public class TransitionReactionBottomPanel extends JPanel {
    
    public TransitionReactionBottomPanel(Global g, TransitionReaction reaction){
        
        this.setLayout(new BorderLayout());
        
        JTable table = new JTable(new TransitionReactionTableModel(reaction));
        table.setDefaultEditor(Double.class, new ValueCellEditor(ValueTextField.DOUBLE, Constraints.NONNEGATIVE, false));
        table.setDefaultEditor(String.class, new ReactionNameCellEditor(g, reaction));
        
        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setPreferredSize(new Dimension(300,50));
//        JPanel scrollPanel = new JPanel();
//        scrollPanel.setLayout(new BorderLayout());
//        scrollPanel.add(scrollPane, "Center");
        
        AnnotationTextArea ata = new AnnotationTextArea(reaction.getAnnotation());
        JScrollPane taScrollPane = new JScrollPane(ata);
        taScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        taScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        
        JLabel annotationLabel = new JLabel("Annotation", JLabel.CENTER);
        annotationLabel.setFont(Fonts.SUBTITLEFONT);
        
        JPanel p = new JPanel();
        p.setLayout(new BorderLayout());
        p.setPreferredSize(new Dimension(300,300));
        p.add(annotationLabel, "North");
        p.add(taScrollPane, "Center");
        
        this.add(scrollPane, "North");
        this.add(p, "Center");
        
    }
    
}
