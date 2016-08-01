package com.juanhoo.Controller;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
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
    public final static String FIRSTINSTALLTIME = "firstInstallTime";
    public final static String LASTUPDATETIME = "lastUpdateTime";
    LinkedList<String> keys = new LinkedList<>(Arrays.asList("userId", "codePath", VERSION, "flags", FIRSTINSTALLTIME, LASTUPDATETIME, "installerPackageName"));
    public boolean isThirdPatyApp = false;
    public boolean isUpdated = false;


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

    public boolean isThirdPartyAPP() {
        String firstInstallTime = packageDetailMap.get(FIRSTINSTALLTIME);

        DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
        try {
            Date installDate = format.parse(firstInstallTime);
            Calendar cal = Calendar.getInstance();
            cal.setTime(installDate);
            int installYear = cal.get(Calendar.YEAR);
            if (installYear < 2015) {
                return false;
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return true;
    }

    public boolean isUpdated() {
        String firstInstallTime = packageDetailMap.get(FIRSTINSTALLTIME);
        String lastUpdateTime = packageDetailMap.get(LASTUPDATETIME);

        if (firstInstallTime.compareTo(lastUpdateTime) !=0 ) {
            return true;
        }
        return false;
    }
}
