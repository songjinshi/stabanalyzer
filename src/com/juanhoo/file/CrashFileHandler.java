package com.juanhoo.file;

import com.juanhoo.Controller.CrashStack;
import com.juanhoo.Controller.Parser;
import com.juanhoo.Controller.Tombstone;

import java.io.*;

/**
 * Created by JH on 2016/7/6.
 */
public class CrashFileHandler extends FileHandler{

    public CrashFileHandler(Parser p) {
        super(p);
    }

    @Override
    public boolean process() {
        String CRASHSTACKSTART = "FATAL EXCEPTION";
        String TOMBSTONESTART = "DEBUG   : *** *** *** *** *** *** *** *** *** *** *** *** *** *** *** ***";
        String TOMBSTONEEND ="DEBUG   : Tombstone written to:";
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
                //Parse crash
                if (line.contains(CRASHSTACKSTART)) {
                    if (cs != null) {
                        parser.addCrashStack(cs);
                    }
                    cs = new CrashStack();

                }
                if ((line.length() == 0 || !line.contains("AndroidRuntime")) && cs != null) {
                    parser.addCrashStack(cs);
                    cs = null;
                }

                if (cs != null) {
                    cs.addLine(line);
                    continue;
                }
                //Parse TOMBSTONE
                if (line.contains(TOMBSTONESTART)) {
                    if (tombstone == null) {
                        //Add tombstone
                        tombstone = new Tombstone();

                    }
                }
                if (line.contains(TOMBSTONEEND)) {
                    if (tombstone != null) {
                        parser.addTombstone(tombstone);
                        tombstone = null;
                    }
                }

                if (tombstone != null) {
                    tombstone.AddLine(line);
                }

            }
            if (cs != null) {
                parser.addCrashStack(cs);
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
