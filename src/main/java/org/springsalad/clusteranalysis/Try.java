package org.springsalad.clusteranalysis;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.DoubleStream;

public class Try {
	public static void main(String[] args) {
		Path filePath = Paths.get("t","r"+String.format("_%."+4+"f_",0.0134234)+"lah.csv");
		System.out.println(filePath);
		String str1 = "MEAN_Run"; String str2 = "OVERALL_Run";
		System.out.println(str1 + " " + str1.compareTo(str2) + " " + str2);
	}

}
