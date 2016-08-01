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
 * Created by Yi He on 7/17/2016.
 */
public class Watchdog extends Issue{
    ArrayList<String> blockedSysThreadNameList = new ArrayList<>();

    public Watchdog(IssueType type, String line){
        issueType = type;
        String pattern = "^(\\d{1,2})-(\\d{1,2})\\s+(\\d{1,2}):(\\d{1,2}):(\\d{1,2}).(\\d{1,3})\\s+(\\d{1,})\\s+(\\d{1,})\\s+\\w{1}\\s+Watchdog: \\*\\*\\* WATCHDOG KILLING SYSTEM PROCESS:(.*)$";
        Matcher match = Pattern.compile(pattern).matcher(line);
        if (match.find()) {
            String wathcdogTime = "2016-"+match.group(1)+"-"+match.group(2)+" "+match.group(3)+":"+match.group(4)+":"+match.group(5)+"."+match.group(6);
            DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH);
            try {
                time = format.parse(wathcdogTime);
                pid = Integer.parseInt(match.group(7));
                pName = "System_server";
            } catch (ParseException e) {
                e.printStackTrace();
            }
            String[] watchdogDetails = match.group(9).split(",");
            for (String detail:watchdogDetails) {
                pattern = ".*\\((.*)\\)$";
                match = Pattern.compile(pattern).matcher(detail);
                if (match.find()) {
                    blockedSysThreadNameList.add(match.group(1));
                }
                reason += "\n"+detail;
            }
        }
    }

    public void addLine(String line) {
        if (!line.isEmpty()) {
            logData = logData + line + "\n";
        }
    }


}
