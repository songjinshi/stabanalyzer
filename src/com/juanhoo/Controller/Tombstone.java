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
 * Created by JH on 2016/7/6.
 */
public class Tombstone {
    public int pid;
    public int tid;
    public String pName;
    public String tName;
    public Date time = null;
    public String logData = "";
    public String reason;
    public String buildFingerPrint= "";

//Tombstone in crash.txt, there is DEBUG AND TIME PREFIX, BUT IN ENTRY.TXT OR DROPBOX.TXT,NO DEBUG AND TIME PREFIX
    public void AddLine(String line) {

        if (time == null) {
            String pattern = "^(\\d{1,2})-(\\d{1,2})\\s+(\\d{1,2}):(\\d{1,2}):(\\d{1,2}).(\\d{1,3})\\s+(\\d{1,})\\s+(\\d{1,})\\s+\\w{1}\\s+(.*)";
            Matcher match = Pattern.compile(pattern).matcher(line);
            if (match.find()) {
                String tombstoneTime = "2016-"+match.group(1)+"-"+match.group(2)+" "+match.group(3)+":"+match.group(4)+":"+match.group(5)+"."+match.group(6);
                DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH);
                try {
                    time = format.parse(tombstoneTime);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
        }


        String pattern = "(.*)pid:\\s+(\\d+),\\s+tid:\\s+(\\d+),\\s+name:\\s+(.*)\\s+>>>\\s+(.*?)\\s+<<<";
        Matcher match = Pattern.compile(pattern).matcher(line);
        if (match.find()) {
            pid = Integer.parseInt(match.group(2));
            pName = match.group(5);
            tid = Integer.parseInt(match.group(3));
            tName = match.group(4);
            return;
        }
        //Build fingerprint: 'motorola/addison/addison:6.0.1/MPN24.94/2146:userdebug/intcfg,test-keys'
        pattern = ".*Build fingerprint:\\s+'(.*)'$";
        match = Pattern.compile(pattern).matcher(line);
        if (match.find()) {
           buildFingerPrint = match.group(1);
        }

//05-02 21:36:56.407   476   476 F DEBUG   : signal 6 (SIGABRT), code -6 (SI_TKILL), fault addr --------

        pattern = "(.*)signal\\s+(\\d+)\\s+(.*),\\scode\\s+(.*)\\s+(.*),\\sfault addr(.*)";
        match = Pattern.compile(pattern).matcher(line);
        if (match.find()) {
            reason = "Signal "+match.group(2)+ " "+match.group(3) + " Code "+ match.group(4) +" " +
                    match.group(5) + " fault address: "+ match.group(6);

        }

        logData +=  line +"\n";
    }

    public boolean isDup(Tombstone target) {
        if (pid == target.pid && tid == target.tid) return true;
        else return false;
    }

    @Override
    public boolean equals(Object obj) {

        return false;
    }

    @Override
    public String toString() {
        String data ="Process: "+pName+ ("pid="+pid +")  Thread name: "+tName+ "(tid = "+tid+")\n");
        data += logData;
        return data;
    }
}
