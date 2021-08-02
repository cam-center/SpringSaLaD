package org.springsalad.clusteranalysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NavigableMap;

import javax.swing.table.AbstractTableModel;

import org.springsalad.helpersetup.IOHelp;

class ClusterAveragesTableModel extends AbstractTableModel{
	
	private static final long serialVersionUID = 2805397188769564278L;
	
	
	private final List<String> namesToShow;
    private final NavigableMap<String,DataFrame> dataFrameMap;
    private boolean mapAllNulls;
    private DataFrame firstNonNullDF;
    
	public ClusterAveragesTableModel(NavigableMap<String, DataFrame> dataFrameMap) {
		namesToShow = new ArrayList<>();
		this.dataFrameMap = dataFrameMap;
		
		mapAllNulls = true;
		firstNonNullDF = null;
		for (DataFrame df: this.dataFrameMap.values()) {
			if (df != null) {
				mapAllNulls = false;
				firstNonNullDF = df;
				break;
			}
		}
		
		determineTimeUnit();
	}
	
    // For display purposes it helps to pick a convenient unit for the time,
    // for example, ms instead of seconds.
    private String unit;
    private double unitScale;


    /* ****************** DETERMINE UNIT AND SCALE ***********************/
    private void determineTimeUnit(){
        // <editor-fold defaultstate="collapsed" desc="Method Code">
    	if (mapAllNulls) {
    		unit = "s";
    		unitScale = 1;
    	}
    	else {
    		List<Object> times = firstNonNullDF.getSeries(0);
	        if(times.size() > 1){
	            double dt = (Double)times.get(1);
	            if(dt >= 0.1){
	                unit = "s";
	                unitScale = 1;
	            } else if(1E3*dt >= 0.1){
	                unit = "ms";
	                unitScale = 1E3;
	            } else if(1E6*dt >= 0.1){
	                unit = "us";
	                unitScale = 1E6;
	            } else if(1E9*dt >= 0.1){
	                unit = "ns";
	                unitScale = 1E9;
	            } else {
	                unit = "ps";
	                unitScale = 1E12;
	            }
	        }
    	}       
        // </editor-fold>
    }

    public Iterable<String> getNames(){
    	return dataFrameMap.keySet();
    }
    
    public void replaceAllNamesToShow(List<String> newNamesToShow){
        namesToShow.clear();
        namesToShow.addAll(newNamesToShow);
        fireTableStructureChanged();
    }

    /* *****************  TABLE METHODS **********************************/

    @Override
    public int getRowCount() {
    	if (mapAllNulls) {
    		return 1;
    	}
    	else {
    		return firstNonNullDF.getSeries(0).size();
    	}
    }

    @Override
    public int getColumnCount() {
        return 1 + 3*namesToShow.size();
    }

    @Override
    public String getColumnName(int col){
        if(col == 0){
            return "Time (" + unit + ")";
        }
        else{
        	String detailStr;
        	switch ((col-1) % 3){
        		case 0:
        			detailStr = "ACS";
        			break;
        		case 1:
        			detailStr = "SD";
        			break;
        		default: // mod == 2
        			detailStr = "ACO";
        	}
            return namesToShow.get((col-1)/3) + " " + detailStr;
        }
    }

    @Override
    public Object getValueAt(int row, int col) {
    	if (mapAllNulls) {
    		return "NA";
    	}
    	else {
	        if(col == 0){
	            return IOHelp.DF[3].format(unitScale * (Double) firstNonNullDF.getSeries(0).get(row));
	        }
	        else{    
	        	DataFrame df = dataFrameMap.get(namesToShow.get((col-1)/3));
	        	if (df == null) {
	        		return "NA";
	        	}
	        	else {
	        		return IOHelp.DF[3].format((Double) df.getSeries((col-1)%3+1).get(row));
	        	}
	        }
    	}
    }

    @Override
    public Class getColumnClass(int c){
        return String.class;
    }

}
