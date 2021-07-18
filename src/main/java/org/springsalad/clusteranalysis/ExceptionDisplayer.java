package org.springsalad.clusteranalysis;

import java.util.Arrays;
import java.util.function.Consumer;

public class ExceptionDisplayer {
    public static void justDisplayException(String extraMessage, Exception exception){
        System.out.print(extraMessage);
        System.out.println(exception.getClass().getName() + " : " + exception.getMessage());
        for (StackTraceElement s: exception.getStackTrace()){
            System.out.println("\tat " + s);
        }
    }
}
