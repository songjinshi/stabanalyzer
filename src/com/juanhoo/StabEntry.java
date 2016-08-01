package com.juanhoo;

import com.juanhoo.Controller.Parser;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Yi He on 8/1/2016.
 * 
 */
public class StabEntry {
    Parser parser;
    public StabEntry() {
        parser = new Parser();
    }

    private String handleInputFile(String inputFile) {

        File file = new File(inputFile);
        if (!file.exists()) {
            System.out.println("The input file or folder don't exist!!!!");
            //Handle if it is a bug2Go ID
            return null;
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
                return null;
            }
            File destDir = new File(folder);
            if (!destDir.exists()) {
                if (!destDir.mkdir()) {
                    System.out.println("Failed to create directory " + folder);
                    return null;
                }
            }
            if (!Parser.unZIPFile(inputFile, folder)) {
                System.out.println("Extract log file " + inputFile + " failed!");
                return null;
            }
        } else {
            folder = inputFile;
        }

        return folder;
    }


    public boolean process(String fileName) {

        String folder = handleInputFile(fileName);
        if (folder == null) {
            return false;
        }

        parser.setLogFolder(folder);
        parser.process();
        return true;
    }


    public String getJiraComments() {
        return parser.getJiraComments();
    }

    public String getHtmlComments(){
        return parser.getHtmlComments();
    }

}
