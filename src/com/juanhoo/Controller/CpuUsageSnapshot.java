package com.juanhoo.Controller;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Yi He on 7/10/2016.
 */
public class CpuUsageSnapshot {

    public Date time;
    public int start;
    public int end;
    public ArrayList<CpuUsage> cpuUsages;
    public String logData = "";

    static public class CpuUsage {
        public double percentage;
        public int pid;
        public String processName;
    }

    public CpuUsageSnapshot(String line) {
        String pattern = "(^[\\d-]{5})\\s++([\\d:.]{12})\\s++([\\d]{1,5}+)\\s++([\\d]{1,5}+)(.*?)ActivityManager:\\s+CPU\\s+usage\\s+from\\s+([-\\d]+)ms\\s+to\\s+([-\\d]+)ms.*";
        Matcher match = Pattern.compile(pattern).matcher(line);
        if (match.find()) {
            String anrTimeStr = "2016-"+match.group(1)+" "+match.group(2);
            DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH);
            try {
                time = format.parse(anrTimeStr);
            } catch (ParseException e) {
                e.printStackTrace();
            }
            start = Integer.parseInt(match.group(6));
            end = Integer.parseInt(match.group(7));
        } else {
            assert false;
        }
        cpuUsages = new ArrayList<>();
    }

    public void addCpuUsage(CpuUsage cu) {
        if (cu.percentage == 0) {
            return;
        }
        cpuUsages.add(cu);
    }

    public void addLine(String line) {
        logData += line +"\n";
    }

}
