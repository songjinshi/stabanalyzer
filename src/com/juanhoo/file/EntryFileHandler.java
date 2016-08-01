package com.juanhoo.file;

import com.juanhoo.Controller.CrashStack;
import com.juanhoo.Controller.Issue;
import com.juanhoo.Controller.Parser;
import com.juanhoo.Controller.Tombstone;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

/**
 * Created by Yi He on 7/8/2016.
 * Only catch tombstone.
 */
public class EntryFileHandler extends FileHandler {

    public EntryFileHandler(Parser p) {
        super(p);
    }

    @Override
    public boolean process() {
            String CRASHSTACKSTART = "FATAL EXCEPTION";
            String TOMBSTONESTART = "*** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***";
            String BACKTRACESTART = "backtrace";
            String BACKTRACEEND ="stack";
            //06-24 21:30:40.301   774   774 F DEBUG   : *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***
            //06-24 21:30:40.301   774   774 F DEBUG   : pid: 777, tid: 31061, name: NPDecoder-CL  >>> /system/bin/mediaserver <<<
            BufferedReader br = null;
            try {
                br = new BufferedReader(new FileReader(fileName));
                CrashStack cs = null;
                Tombstone tombstone = null;
                String line;
                while ((line = br.readLine()) != null) {
                    showProgress();
                    //Parse TOMBSTONE
                    if (line.contains(TOMBSTONESTART) && tombstone == null) {
                            //Add tombstone
                        tombstone = new Tombstone(Issue.IssueType.TOMBSTONE, line);
                        parser.addIssue(tombstone);

                    }
                    if (tombstone != null && line.contains(BACKTRACEEND)) {
                        tombstone = null;
                    }

                    if (tombstone != null) {
                        tombstone.AddLine(line);
                    }


                }
            } catch (FileNotFoundException e) {
                System.out.println("Couldn't open file " + fileName);
                e.printStackTrace();
            } catch (IOException e) {
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
