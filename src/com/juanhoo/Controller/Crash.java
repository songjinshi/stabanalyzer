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
public class Crash {
    public int pid;
    public int uid;
    public String name;
    public Date time;
    public String reason;
    public String logData;
    public int dupInd = -1;
//03-11 06:00:08.806  2371  2427 I am_anr  : [0,24898,com.lifx.lifx,948485700,Broadcast of Intent { act=android.net.wifi.STATE_CHANGE flg=0x4000010 cmp=com.lifx.lifx/com.lifx.app.wear.WifiReceiver (has extras) }]

    public Crash(String line) {
        String patternANR = "^(\\d{1,2})-(\\d{1,2})\\s+(\\d{1,2}):(\\d{1,2}):(\\d{1,2}).(\\d{1,3})\\s+(\\d{1,})\\s+(\\d{1,})\\s+\\w{1}\\s+am_crash:\\s+\\[(\\d{1,}),(\\d{1,}),(.+),([-\\d]{1,}),(.+)\\]$";

        Matcher match = Pattern.compile(patternANR).matcher(line);
        if (match.find()) {
            //We used for 2016
            String crashTime = "2016-"+match.group(1)+"-"+match.group(2)+" "+match.group(3)+":"+match.group(4)+":"+match.group(5)+"."+match.group(6);
            DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH);
            try {
                time = format.parse(crashTime);


            } catch (ParseException e) {
                e.printStackTrace();
            }
            pid = Integer.parseInt(match.group(9));
            uid = Integer.parseInt(match.group(10));
            name = match.group(11);
            reason = match.group(13);
            logData = line;
        } else {
            assert false;
        }
    }


}
