package com.juanhoo.file;

import com.juanhoo.Controller.Parser;

import java.io.File;

/**
 * Created by JH on 2016/7/6.
 */
public abstract class FileHandler {

    String fileName;
    Parser parser;
    String result = "";

    public FileHandler(Parser p) {
        parser = p;
    }

    public boolean open(String name) {
        if (name == null) {
            return false;
        }
        File inputFile = new File(name);
        if (!inputFile.exists() || inputFile.isDirectory()) {
            System.out.println("File " + name + " doesn't exist!");
            return false;
        }

        fileName = name;
        return true;
    }

    public abstract boolean process() ;

    static int count = 0;
    public void showProgress() {
        if (count++ == 200) {
            count = 0;
            System.out.print(".");
        }
    }

}
