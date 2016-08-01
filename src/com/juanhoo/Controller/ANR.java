package com.juanhoo.Controller;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by JH on 2016/7/6.
 */
public class ANR extends Issue{

    public ANR(IssueType type, String line) {

        issueType = type;
        String patternANR = "^(\\d{1,2})-(\\d{1,2})\\s+(\\d{1,2}):(\\d{1,2}):(\\d{1,2}).(\\d{1,3})\\s+(\\d{1,})\\s+(\\d{1,})\\s+\\w{1}\\s+am_anr\\s+:\\s+\\[(\\d{1,}),(\\d{1,}),(.+),(.*),(.*)\\]$";

        Matcher match = Pattern.compile(patternANR).matcher(line);
        if (match.find()) {
            //We used for 2016
            String anrTimeStr = "2016-"+match.group(1)+"-"+match.group(2)+" "+match.group(3)+":"+match.group(4)+":"+match.group(5)+"."+match.group(6);
            DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH);
            try {
                time = format.parse(anrTimeStr);
            } catch (ParseException e) {
                e.printStackTrace();
            }
            pid = Integer.parseInt(match.group(10));
            uid = Integer.parseInt(match.group(9));
            pName = match.group(11);
            reason = match.group(13).replaceAll("\\{","\\\\{").replaceAll("\\}","\\\\}");  //Used to format

            logData = line;
        } else {
            assert false;
        }
    }
}
