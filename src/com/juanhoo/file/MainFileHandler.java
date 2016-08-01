package com.juanhoo.file;

import com.juanhoo.Controller.Issue;
import com.juanhoo.Controller.ModemPanic;
import com.juanhoo.Controller.Parser;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

/**
 * Created by Yi He on 7/12/2016.
 */
public class MainFileHandler extends FileHandler{


    public MainFileHandler(Parser p) {
        super(p);
    }

    @Override
    public boolean process() {
        BufferedReader br = null;
        String SSRENTRY = "pass_ramdump enter";
        String SSREXIT = "pass_ramdump exit";
        try {
            br = new BufferedReader(new FileReader(fileName));
            ModemPanic modemPanic = null;
            String line;
            while ((line = br.readLine()) != null) {
                showProgress();
                if (line.contains(SSRENTRY)) {
                    modemPanic = new ModemPanic(Issue.IssueType.MODEMPANIC, line);
                    continue;
                }

                if (line.contains(SSREXIT)) {
                    if (modemPanic != null) {
                        parser.addIssue(modemPanic);
                        modemPanic = null;
                        continue;
                    }
                }
                if (modemPanic != null && line.contains("SSR")) {
                    modemPanic.addLine(line);
                }
                parser.saveLogByCrashPid(line);
            }
        } catch (FileNotFoundException e) {
            System.out.println("Couldn't open file " + fileName);
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

}
