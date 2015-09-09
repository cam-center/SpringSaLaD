/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package langevinsetup;

import helpersetup.Colors;
import helpersetup.Constraints;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class DrawPanel3DPanel extends JPanel implements ActionListener {
    
    private DrawPanel3D drawPanel;
    
    private final ValueTextField [] shiftTF = new ValueTextField[3];
    private JButton shift;
    
    public DrawPanel3DPanel(DrawPanel3D drawPanel){
        this.setLayout(new BorderLayout());
        this.drawPanel = drawPanel;
        drawPanel.systemSetup();
        
        this.add(drawPanel, "Center");
        this.add(makeButtonPanel(), "East");
        
        shift.addActionListener(this);
    }
    
    private JPanel makeButtonPanel(){
        
        JPanel p = new JPanel();
        p.setLayout(new GridLayout(5,1));
        p.setPreferredSize(new Dimension(150,160));
        
        JLabel translationLabel = new JLabel("Set Translation (nm)", JLabel.CENTER);
        p.add(translationLabel);
        
        JLabel [] label = new JLabel[]{new JLabel("dx: ", JLabel.RIGHT), 
            new JLabel("dy: ", JLabel.RIGHT), new JLabel("dz: ", JLabel.RIGHT)};
        
        JPanel [] shiftPanel = new JPanel[3];
        for(int i=0;i<3;i++){
            shiftPanel[i] = new JPanel();
            shiftPanel[i].add(label[i]);
            shiftTF[i] = new ValueTextField("0.0", 5, ValueTextField.DOUBLE, Constraints.NO_CONSTRAINT, false);
            shiftPanel[i].add(shiftTF[i]);
            p.add(shiftPanel[i]);
        }
        
        JPanel buttonPanel = new JPanel();
        shift = new JButton("Translate");
        buttonPanel.add(shift);
        p.add(buttonPanel);
        
        JPanel rPanel = new JPanel();
        rPanel.add(p);
        return rPanel;
    }
    
    @Override
    public void actionPerformed(ActionEvent event){
        JButton source = (JButton)event.getSource();
        if(source == shift){
            drawPanel.shiftSites(shiftTF[0].getDouble(), shiftTF[1].getDouble(), shiftTF[2].getDouble());
        }
    }
    
    public static void main(String [] args){
        JFrame frame = new JFrame();
        
        Molecule molecule = new Molecule("H");
        SiteType [] type = new SiteType[Colors.COLORARRAY.length];
        for(int i=0;i<type.length;i++){
            type[i] = new SiteType(molecule, "Type " + i);
            type[i].setRadius(1 + 0.5*(i%3));
            type[i].setColor(Colors.COLORARRAY[i]);
            molecule.addType(type[i]);
        }
        Site [] site = new Site[3*type.length];
        for(int i=0;i<site.length;i++){
            site[i] = new Site(molecule, type[i%type.length]);
            site[i].setX(30*Math.cos(2*Math.PI*i/site.length));
            site[i].setY(30*Math.sin(2*Math.PI*i/site.length));
            site[i].setZ(6);
            site[i].setLocation(SystemGeometry.INSIDE);
            molecule.addSite(site[i]);
        }
        Link [] link = new Link[site.length -1];
        for(int i=0;i<link.length;i++){
            link[i] = new Link(site[i], site[i+1]);
            molecule.addLink(link[i]);
        }
        
        DrawPanel3D panel3d = new DrawPanel3D(molecule);
        
        DrawPanel3DPanel panel = new DrawPanel3DPanel(panel3d);
        
        
        Container c = frame.getContentPane();
        c.add(panel);
        
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setSize(600,400);
        frame.setVisible(true);
    }
    
}
