package com.juanhoo.Controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Yi He on 7/15/2016.
 */
public class PackageInfo {
    public String packageName;
    public void SetPackageName (String name) {
        packageName = name;
    }
    HashMap<String, String> packageDetailMap = new HashMap<>();
    public final static String VERSION =  "versionName";
    LinkedList<String> keys = new LinkedList<>(Arrays.asList("userId", "codePath", VERSION, "flags", "firstInstallTime", "lastUpdateTime", "installerPackageName"));


    @Override
    public String toString() {
        String result = "<b>"+packageName+"</b>\n";
        for (String key: keys) {
            result = result + "<br> "+ key+ " "+ packageDetailMap.get(key);
        }
        return result;
    }

    public String getKey(String key) {
        return packageDetailMap.get(key);
    }

    //Pattern.compile("Bootloader: (.*?)$")
    public void Parse(String line) {
        if (keys.size() == 0) {
            return;
        }
        for (String key:keys) {
            String pattern = key+"=(.*?)$";
            Matcher match = Pattern.compile(pattern).matcher(line);
            if (match.find()) {
                packageDetailMap.put(key, match.group(1));
            }
        }
    }
}
