package com.juanhoo.file;

import com.juanhoo.Controller.ANR;
import com.juanhoo.Controller.Crash;
import com.juanhoo.Controller.Issue;
import com.juanhoo.Controller.Parser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Created by JH on 2016/7/6.
 */
public class EventFileHandler extends FileHandler {

    public EventFileHandler(Parser p) {
        super(p);
    }

    @Override
    public boolean process() {

        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(fileName));
            String line;
            while ((line = br.readLine()) != null) {
                showProgress();
                if (line.contains("am_anr")) {
                    Issue anr = new ANR(Issue.IssueType.ANR, line);
                    parser.addIssue(anr);
                    continue;
                }
                if (line.contains("am_crash")) {
                    Issue crash = new Crash(Issue.IssueType.FORCECLOSE, line);
                    parser.addIssue(crash);
                    continue;
                }

                if (line.contains("am_proc_bound")) {
                    parser.extractPID(line);
                }
            }
            //Should we loop it again to store process? We loop the event log twice.
            br = new BufferedReader(new FileReader(fileName));
            while ((line = br.readLine()) != null) {
                showProgress();
                parser.saveLogByCrashPid(line);
            }
            //End
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


}
