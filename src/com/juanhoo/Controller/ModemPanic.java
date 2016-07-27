package com.juanhoo.Controller;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Yi He on 7/12/2016.
 */
public class ModemPanic {
    public Date time;
    String logData = "";
    String subtype;
    String errmsg;
    String reason = " ";

    public void addLine(String line) {
        String subtypePattern = "^([\\d\\-]{1,})\\s+([\\d\\:\\.]+)\\s+(\\d{1,})\\s+(\\d{1,})\\s+(\\w{1})\\s+SSR\\s+:\\s+subtype is\\s(.*)";
        String errmsgPattern = "^([\\d\\-]{1,})\\s+([\\d\\:\\.]+)\\s+(\\d{1,})\\s+(\\d{1,})\\s+(\\w{1})\\sSSR\\s+:\\s+errmsg is\\s+(.*)" ;

        Matcher match = Pattern.compile(subtypePattern).matcher(line);
        if (match.find()) {

            subtype = match.group(6);
            reason += "\nSubtype : "+subtype +"\n";

            String crashTime = "2016-" + match.group(1)+" "+match.group(2);
            DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH);
            try {
                time = format.parse(crashTime);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }

        match = Pattern.compile(errmsgPattern).matcher(line);
        if (match.find()) {
            errmsg = match.group(6);
            reason += "Error Message : "+errmsg +"\n";
        }
        logData += line +"\n";
    }


}
