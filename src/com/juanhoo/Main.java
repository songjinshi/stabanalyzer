package com.juanhoo;

import com.juanhoo.Controller.Parser;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    static ArrayList<String> handleInputFile(String[] files) {
        ArrayList<String> folders = new ArrayList<>();

        for (String inputFile:files) {
            File file = new File(inputFile);
            if (!file.exists()) {
                System.out.println("The input file or folder don't exist!!!!");
                //Handle if it is a bug2Go ID
                continue;
            }
            String fileNamePatttern = "(.*?)\\.zip$";
            String folder;

            if (file.isFile()) {
                System.out.println(inputFile + " is a file!");
                Matcher match = Pattern.compile(fileNamePatttern).matcher(inputFile);
                if (match.find()) {
                    folder = match.group(1);
                } else {
                    System.out.println("Only accept zip or gz file!");
                    continue;
                }
                File destDir = new File(folder);
                if (!destDir.exists()) {
                    if (!destDir.mkdir()) {
                        System.out.println("Failed to create directory "+folder);
                        return folders;
                    }
                }
                if (!Parser.unZIPFile(inputFile, folder)) {
                    System.out.println("Extract log file "+inputFile+ " failed!");
                }
            } else {
                folder = inputFile;
            }
            folders.add(folder);

        }
        return folders;
    }

    public static void main(String[] args) {
	// write your code here
        System.out.println("Start new Trip!!!!");
        Date startDate = new Date();
        long startTime = System.currentTimeMillis();

        if (args.length == 0) {
            System.out.println("Please specify the log file or the log directory.");
            return;
        }
        ArrayList<String> folders = handleInputFile(args);
        Parser parser = new Parser();
        for (String folder:folders) {
            parser.setLogFolder(folder);
            parser.process();
        }
        long endTime = System.currentTimeMillis();
        Date endDate = new Date();

        System.out.println("\nTotal time cost is "+ (endTime-startTime)/1000 +"seconds");
        System.out.println("\nTotal time cost is "+ (endDate.getTime() - startDate.getTime())/1000 +"seconds");
    }


}
