package org.springsalad.clusteranalysis;

public class ExceptionDisplayer {
    public static void justDisplayException(String extraMessage, Exception exception){
        System.out.print(extraMessage);
        System.out.println(exception.getClass().getName() + " : " + exception.getMessage());
        for (StackTraceElement s: exception.getStackTrace()){
            System.out.println("\tat " + s);
        }
    }
}
