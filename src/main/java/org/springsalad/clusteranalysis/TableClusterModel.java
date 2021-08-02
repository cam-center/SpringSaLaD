package org.springsalad.clusteranalysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.table.AbstractTableModel;

import org.springsalad.helpersetup.IOHelp;

public class TableClusterModel extends AbstractTableModel {
	// FIXME change implementation
	
    private final ArrayList<String> namesToShow;
    private final String[] names;
    private final DataFrame clusterDataFrame;

    // For display purposes it helps to pick a convenient unit for the time,
    // for example, ms instead of seconds.
    private String unit;
    private double unitScale;

    public TableClusterModel(DataFrame clusterDataFrame){
        namesToShow = new ArrayList<>();
        names = Arrays.stream(clusterDataFrame.headers, 1, clusterDataFrame.headers.length)
                .toArray(String[]::new);
        this.clusterDataFrame = clusterDataFrame;
        determineTimeUnit();
    }

    public String[] getTableNames(){
        return names;
    }

    /* ****************** DETERMINE UNIT AND SCALE ***********************/
    private void determineTimeUnit(){
        // <editor-fold defaultstate="collapsed" desc="Method Code">
        List<Object> times = clusterDataFrame.getSeries(0);
        if(times.size() > 1){
            double dt = (Double)times.get(1);
            if(dt > 0.1){
                unit = "s";
                unitScale = 1;
            } else if(1E3*dt > 0.1){
                unit = "ms";
                unitScale = 1E3;
            } else if(1E6*dt > 0.1){
                unit = "us";
                unitScale = 1E6;
            } else if(1E9*dt > 0.1){
                unit = "ns";
                unitScale = 1E9;
            } else {
                unit = "ps";
                unitScale = 1E12;
            }
        }
        // </editor-fold>
    }

    /* ****************** MODIFY THE NAMES TO SHOW LIST ******************/
    public void addNameToShow(String name){
        if(!namesToShow.contains(name)){
            namesToShow.add(name);
            fireTableStructureChanged();
        }
    }

    public void replaceAllNamesToShow(List<String> newNamesToShow){
        namesToShow.clear();
        namesToShow.addAll(newNamesToShow);
        fireTableStructureChanged();
    }

    public void removeNamesToShow(String name){
        namesToShow.remove(name);
        fireTableStructureChanged();
    }

    public void clearNamesToShow(){
        namesToShow.clear();
        fireTableStructureChanged();
    }

    public ArrayList<String> getNamesToShow(){
        return namesToShow;
    }

    /* *****************  TABLE METHODS **********************************/

    @Override
    public int getRowCount() {
        return clusterDataFrame.getSeries(0).size();
    }

    @Override
    public int getColumnCount() {
        return 1+namesToShow.size();
    }

    @Override
    public String getColumnName(int col){
        if(col == 0){
            return "Time (" + unit + ")";
        }
        else{
            return namesToShow.get(col-1);
        }
    }

    @Override
    public Object getValueAt(int row, int col) {
        if(col == 0){
            return IOHelp.DF[3].format(unitScale * (Double)clusterDataFrame.getSeries(0).get(row));
        }
        else{
            return clusterDataFrame.getSeries(namesToShow.get(col-1)).get(row);
        }
    }

    @Override
    public Class getColumnClass(int c){
        if(c == 0){
            return String.class;
        } else {
            return Double.class;
        }
    }

    // Nothing is editable
    @Override
    public boolean isCellEditable(int row, int col){
        return false;
    }

    @Override
    public void setValueAt(Object value, int row, int col){}


}
