/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.springsalad.runlauncher;

import java.io.*;

public class RunLauncher implements Runnable {
    
    private final Process [] process;
    private final Thread t;
    private final int totalRuns;
    private final File inputFile;
    private final File [] outputFile;
    private final boolean parallel;
    private final int numberSimultaneous;
    
    public RunLauncher(Process [] process, File inputFile, File [] outputFile, boolean parallel, int numberSimultaneous){
        this.inputFile = inputFile;
        this.totalRuns = outputFile.length;
        this.outputFile = outputFile;
        this.t = new Thread(this);
        this.process = process;
        this.parallel = parallel;
        this.numberSimultaneous = numberSimultaneous;
    }
    
    private void launchSequentialRuns(){
        // <editor-fold defaultstate="collapsed" desc="Method Code">
        String separator = System.getProperty("file.separator");
        String classPath = System.getProperty("java.class.path");
        String javaHome = System.getProperty("java.home");
        String javaPath = javaHome + separator + "bin" + separator + "java";
        Class clazz = langevinnovis01.Global.class;
        ProcessBuilder builder = null;
        
        //
        // to debug the solver from SpringSaLaD
        //
//        boolean debugSolver = true;
//        if(totalRuns == 1 && debugSolver == true) {
//        	String canonicalName = clazz.getCanonicalName();
//        	String inputPath = inputFile.getAbsolutePath();
//        	String outputPath = outputFile[0].getAbsolutePath();
//        	
//        	String[] args = {inputPath, Integer.toString(0), outputPath};
//        	langevinnovis01.Global.main(args);
//        	return;
//        }
        
        for(int i=0;i<totalRuns;i++){
            String intString = Integer.toString(i);
            if(classPath.contains("SpringSalad-")){
                try{
                    builder = new ProcessBuilder(javaPath, "-Xms64m","-Xmx1024m","-jar", 
                            "LangevinNoVis01.jar", inputFile.getAbsolutePath(),
                            intString, outputFile[i].getAbsolutePath());
                    builder.inheritIO();
                    process[0] = builder.start();
                } catch(IOException ioe2){
                    ioe2.printStackTrace(System.out);
                }
            } else {
                try{
                    builder = new ProcessBuilder(javaPath,"-cp",classPath,
                            clazz.getCanonicalName(), inputFile.getAbsolutePath(),
                            intString, outputFile[i].getAbsolutePath());
                    builder.inheritIO();
                    process[0] = builder.start();
                } catch(IOException ioe){
                    ioe.printStackTrace(System.out);
                }
            }
            if(process[0] != null){
                try{
                    int exitCode = process[0].waitFor();
                    System.out.println("Process returned exit code " + exitCode);
                } catch(InterruptedException ie){
                    ie.printStackTrace(System.out);
                }
            }
        }
        // </editor-fold>
    }
    
    private void launchParallelRuns(){
        // <editor-fold defaultstate="collapsed" desc="Method Code">
        String separator = System.getProperty("file.separator");
        String classPath = System.getProperty("java.class.path");
        String javaHome = System.getProperty("java.home");
        String javaPath = javaHome + separator + "bin" + separator + "java";
        Class clazz = langevinnovis01.Global.class;
        ProcessBuilder builder = null;
        for(int i=0;i<totalRuns;i++){
            String intString = Integer.toString(i);
            // All distributed jars are named SpringSalad-xxx, where xxx is
            // either "win" or "mac" or "linux".  Thus, to see if we're running
            // from a distributed jar, just look for the string "SpringSalad-" 
            // on the classpath.
            if(classPath.contains("SpringSalad-")){
                try{
                    builder = new ProcessBuilder(javaPath, "-Xms64m","-Xmx1024m","-jar", 
                            "LangevinNoVis01.jar", inputFile.getAbsolutePath(),
                            intString, outputFile[i].getAbsolutePath());
                    builder.inheritIO();
                    process[i] = builder.start();
                } catch(IOException ioe2){
                    ioe2.printStackTrace(System.out);
                }
            } else {
                try{
                    builder = new ProcessBuilder(javaPath,"-cp",classPath,
                            clazz.getCanonicalName(), inputFile.getAbsolutePath(),
                            intString, outputFile[i].getAbsolutePath());
                    builder.inheritIO();
                    process[i] = builder.start();
                } catch(IOException ioe){
                    ioe.printStackTrace(System.out);
                }
            }
            if(0 == (i+1)%numberSimultaneous){
                // Wait for all the currently running processes to finish. 
                // WOULD BE BETTER TO LAUNCH ANOTHER AS SOON AS ONE FINISHES,
                // BUT I DON'T HAVE TIME TO FIGURE OUT HOW TO DO THAT.
                for(int j=i-numberSimultaneous+1;j<=i;j++){
                    if(process[j] != null){
                        try{
                            process[j].waitFor();
                        } catch(InterruptedException ie){
                            ie.printStackTrace(System.out);
                        }
                    }
                }
            }
        }
        // </editor-fold>
    }
    
    public void start(){
        t.start();
    }
    
    @Override
    public void run(){
        if(parallel){
            launchParallelRuns();
        } else {
            launchSequentialRuns();
        }
    }
    
}
