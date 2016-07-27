package com.juanhoo.file;

import com.juanhoo.Controller.PackageInfo;
import com.juanhoo.Controller.Parser;
import com.juanhoo.Utils.Tools;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Yi He on 7/9/2016.
 */
//Build fingerprint: 'motorola/addison/addison:6.0.1/MPN24.76/1858:userdebug/intcfg,test-keys'
public class BugreportFileHandler extends FileHandler{
    public BugreportFileHandler(Parser p) {
        super(p);
    }

    @Override
    public boolean process() {
        String psStart = "------ PROCESSES (ps -P) ------";
        BufferedReader br = null;

        try {
            br = new BufferedReader(new FileReader(fileName));
            String line = null;
            boolean handleStart = false;
            boolean processHandled = false;
            boolean packageHandled = false;
            PackageInfo pkg = null;

            while ((line = br.readLine()) != null) {
                showProgress();
                if (parser.buildFingerPrint == null && line.contains("Build fingerprint")) {
                    parser.setBuildFingerPrint(getBuildFingerPrint(line));
                }
                //Add process
                if (!processHandled) {
                    if (line.contains(psStart)) {
                        handleStart = true;
                    }

                    if (!handleStart) {
                        continue;
                    }

                    if (handleStart && line.contains("[ps: ")) {
                        processHandled = true;
                        handleStart = false;
                        continue;
                    }

                    String[] items = line.split("\\s+");
                    if (items.length < 10) {
                        continue;
                    }
                    if (Tools.isNumeric(items[1])) {
                        Integer pid = Integer.parseInt(items[1]);
                        String pName = items[9];
                        parser.addProcessRecord(pid, pName);
                        continue;
                    }
                }

                if (!packageHandled) {
                    if (line.matches("^Packages:.*")) {
                        handleStart = true;
                        continue;
                    }

                    if (!handleStart) {
                        continue;
                    }

                    if (line.length() == 0) {
                        handleStart = false;
                        packageHandled = true;
                    }
                    //  Package [com.google.android.inputmethod.latin] (785fb4e):

                    if (line.indexOf("Package [") != -1) {
                        if (pkg != null) {
                            parser.packageInfoMap.put(pkg.packageName, pkg);
                        }
                        pkg = new PackageInfo();
                        pkg.packageName = line.substring(line.indexOf("[")+1, line.indexOf("]"));
                        continue;
                    }
                    if (pkg != null) {
                        pkg.Parse(line);
                    }
                }
            }
        }  catch (IOException e) {
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

    private String getBuildFingerPrint(String line) {
        String pattern = "Build fingerprint:\\s+'(.*?)'";
        Matcher match = Pattern.compile(pattern).matcher(line);
        if (match.find()) {
            return match.group(1);
        }
        return null;
    }
}
