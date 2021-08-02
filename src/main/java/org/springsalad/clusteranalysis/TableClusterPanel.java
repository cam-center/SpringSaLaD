package org.springsalad.clusteranalysis;

import java.io.IOException;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;

public class TableClusterPanel extends JPanel {

    private TableClusterModel tableClusterModel;

    public TableClusterPanel(Path dataFolder){
        //check that it is a directory

        super();
        Path clusterStatFolder = Paths.get(dataFolder.toString(),"Cluster_stat");
        try{
            DataFrame clusterDataFrame = compileDataFrameFromFiles(clusterStatFolder);

            tableClusterModel = new TableClusterModel(clusterDataFrame);
            JTable jTable = new JTable(tableClusterModel);
            jTable.setFillsViewportHeight(true);
            JScrollPane jScrollPane = new JScrollPane(jTable);
            jScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
            this.add(jScrollPane);
        }
        catch(IOException | DirectoryIteratorException exception){
            JLabel jLabel = new JLabel("Cluster stats unavailable");
            this.add(jLabel);
        }
    }

    @SuppressWarnings("unchecked")
    static DataFrame compileDataFrameFromFiles(Path clusterStatFolder) throws IOException{
        //possible IOE
        DirectoryStream<Path> directoryStream = Files.newDirectoryStream(clusterStatFolder);

        String meanFileName = null;
        List<String> runFileNames = new ArrayList<>();

        //gather file names
        for (Path clusterStatFolderFile: directoryStream){
            String fileName = clusterStatFolderFile.getFileName().toString();

            //sees if it is the mean file
            Pattern pattern1 = Pattern.compile("Clustering_dynamics.csv");
            Matcher matcher1 = pattern1.matcher(fileName);
            if (matcher1.matches()){
                meanFileName = fileName;
            }
            else {
                Pattern pattern2 = Pattern.compile("Run\\d+_clustering_dynamics.csv");
                Matcher matcher2 = pattern2.matcher(fileName);
                if(matcher2.matches()){
                    runFileNames.add(fileName);
                }
            }
        }
        directoryStream.close();

        //check if there actually are any stat files
        if (meanFileName==null || runFileNames.size()==0){
            throw new IOException("No cluster stat files"); //possible IOE
        }

        //sort run-file-names
        runFileNames.sort(null);
        runFileNames.sort(Comparator.comparingInt(String::length));


        //read mean file, adds it as first element to df list
        DataFrame meanDF = null;
        {
            Path clusterStatFolderFile = Paths.get(clusterStatFolder.toString(), meanFileName);
            try {
                meanDF = CSVHandler.readCSV(clusterStatFolderFile, 0);
                String[] newHeaders = Arrays.stream(meanDF.headers).map(a -> "MEAN " + a).toArray(String[]::new); //adds MEAN to all headers
                newHeaders[0] = meanDF.headers[0]; //removes MEAN from the first header (time)
                meanDF.headers = newHeaders;
            }
            catch (IOException | IllegalArgumentException exception){
                ExceptionDisplayer.justDisplayException("Exception reading file: ", exception); //just skip over this exception
            }
        }

        //read other files
        List<DataFrame> runDFList = new ArrayList<>();
        for (String fileName: runFileNames) {
            Path clusterStatFolderFile = Paths.get(clusterStatFolder.toString(),fileName);
            try {
                DataFrame tmpDF = CSVHandler.readCSV(clusterStatFolderFile, 0);
                Pattern pattern2 = Pattern.compile("Run(\\d+)_clustering_dynamics.csv");
                Matcher matcher2 = pattern2.matcher(fileName);
                matcher2.matches();
                String[] newHeaders = Arrays.stream(tmpDF.headers).map(a -> "Run" + matcher2.group(1) + " " + a).toArray(String[]::new); //adds Run# to all headers
                newHeaders[0] = tmpDF.headers[0]; //removes MEAN from the first header (time)
                tmpDF.headers = newHeaders;
                runDFList.add(tmpDF);
            }
            catch (IOException | IllegalArgumentException exception){
                ExceptionDisplayer.justDisplayException("Exception reading file: ", exception); //just skip over this exception
            }
        }

        //combining the run dataFrames and removing the extra time columns
        List<String> dFHeaderList = runDFList.stream().flatMap(DF-> Arrays.stream(DF.headers, 1, DF.headers.length))
                .collect(Collectors.toList());
        List<List<Object>> dFFrameList = runDFList.stream().flatMap(DF -> Arrays.stream(DF.frame,1,DF.frame.length))
                .collect(Collectors.toList());
        if (meanDF != null) { //if reading was unsuccessful. (right now, just skip)
            dFHeaderList.addAll(0, Arrays.asList(meanDF.headers));
            dFFrameList.addAll(0, Arrays.asList(meanDF.frame));
        }
        String[] combinedHeaders = dFHeaderList.toArray(new String[0]);
        List<Object>[] combinedFrame = dFFrameList.toArray(new List[0]);
        return new DataFrame(combinedHeaders, combinedFrame);

    }

    public String[] getTableNames(){
        return tableClusterModel.getTableNames();
    }

    public void replaceAllNamesToShow(List<String> newNamesToShow) {
    	tableClusterModel.replaceAllNamesToShow(newNamesToShow);
    }
    
    public static void main(String[] args) throws IOException{
        DataFrame df = compileDataFrameFromFiles(Paths.get("C:\\Users\\imt_w\\Documents\\SpringSalad\\Clustering_tutorial_01\\Clustering_tutorial_01_SIMULATIONS\\Simulation3_SIM_FOLDER\\data\\Cluster_stat"));
        System.out.println(df);
    }
}

