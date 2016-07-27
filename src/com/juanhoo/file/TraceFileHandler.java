package com.juanhoo.file;

import com.juanhoo.Controller.Parser;
import com.juanhoo.Controller.Process;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;

/**
 * Created by JH on 2016/7/6.
 */
public class TraceFileHandler extends FileHandler{

    private final String BUGREPORTTRACEBEGIN = "------ VM TRACES JUST NOW";
    private final String BEGINLABEL = "----- pid";
    private final String ENDLABEL = "----- end ";
    private Date time = null;

    public TraceFileHandler(Parser p) {
        super(p);
    }


    private boolean isVMTraceEnd(String line) {
        if (line.contains("-----")) {
            if (line.contains("----- pid")) {
                return false;
            }
            return !line.contains("----- end");
        }
        return false;
    }

    @Override
    public boolean process() {

        BufferedReader br = null;
        try {
            Process pstack = null;
            br = new BufferedReader(new FileReader(fileName));
            String line;
            boolean newProcess = false;

            if (fileName.contains("bugreport")) {
                //If bugreport file, we need read until the stack
                while ((line = br.readLine()) != null) {
                    if (line.contains(BUGREPORTTRACEBEGIN)) {
                        break;
                    }
                }
            }
            while ((line = br.readLine()) != null && !line.contains("tombstones")) {
                showProgress();
                if (line.contains(BEGINLABEL) && !newProcess) {
                    newProcess = true;
                    pstack = new Process();
                }
                if (pstack != null) {
                    pstack.addLine(line);
                }


                if (line.contains(ENDLABEL)) {
                    newProcess = true;

                    if (pstack != null) {
                        if (pstack.snapshotTime == null) {
                            pstack.snapshotTime = time;
                        } else {
                            time = pstack.snapshotTime;
                        }
                        parser.addProcessStack(pstack);
                        pstack.checkDeadLock();
                        String noStandThreadInfo = pstack.getNoStandbyThreadStack();
                        if (noStandThreadInfo.length() != 0) {
                            System.out.println("process Name :"+pstack.name+ " PID :"+pstack.pid);
                            //         System.out.println(noStandThreadInfo);
                        }

                    }
                    pstack = new Process();
                }

            }

        } catch (FileNotFoundException e) {
            System.out.println("Couldn't open file " + fileName);
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
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
