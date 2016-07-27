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
public class CrashStack {
    public int pid;
    public int TID;
    public String name;
    public String exception;
    public String reason;
    public Date time;
    public String logData = "";
    public ArrayList<String> funcStack;

    //03-11 10:08:07.479 11063 11063 E AndroidRuntime: process: com.motorola.iqdataupload, pid: 11063
    public void addLine(String line) {
        String processCrashPattern = "^([\\d\\-]{1,})\\s+([\\d\\:\\.]+)\\s+(\\d{1,})\\s+(\\d{1,})\\s+(\\w{1})\\sAndroidRuntime: Process:\\s+(.+),\\s+" +
                "PID:\\s+([\\d]{1,})$";
        String exceptionDetailPattern = "^([\\d\\-]{1,})\\s+([\\d\\:\\.]+)\\s+(\\d{1,})\\s+(\\d{1,})\\s+(\\w{1})\\sAndroidRuntime: (.+?)Exception(.*)" ;


        logData +=  line +"\n";
        Matcher match;
        match = Pattern.compile(processCrashPattern).matcher(line);
        if (match.find()){
            String crashTime = "2016-" + match.group(1)+" "+match.group(2);
            DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH);
            pid = Integer.parseInt(match.group(7));
            TID = Integer.parseInt(match.group(4));
            try {
                time = format.parse(crashTime);
            } catch (ParseException e) {
                e.printStackTrace();
            }
            name = match.group(6);
            return;
        }
        match = Pattern.compile(exceptionDetailPattern).matcher(line);
        if (match.find()) {
            exception = match.group(6)+"Exception";
            reason = match.group(7);
        }
    }

    @Override
    public String toString() {
        String data = "Process "+ name +" (pid "+ pid +") \n";
        data += "Crash stack is below:\n";
        data += logData;
        return data;
    }


    public boolean isDup(CrashStack target) {
        return pid == target.pid;
    }

    @Override
    public boolean equals(Object obj) {
        if (getClass() != obj.getClass()) {
            return false;
        }
        CrashStack target = (CrashStack)obj;

        if (funcStack.size() != target.funcStack.size()) {
            return false;
        }

        for (int i = 0 ; i < funcStack.size(); i++) {
            if (funcStack.get(i).compareTo(target.funcStack.get(i)) != 0) {
                return false;
            }
        }
        return true;
    }
}
