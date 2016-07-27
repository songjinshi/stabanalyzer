package com.juanhoo.file;

import com.juanhoo.Controller.CpuUsageSnapshot;
import com.juanhoo.Controller.Parser;
import com.juanhoo.Controller.Watchdog;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by JH on 2016/7/6.
 */
public class SystemFileHandler extends FileHandler{

    /*
    07-04 08:50:18.920  1468  1533 E ActivityManager: CPU usage from 0ms to 5841ms later:
07-04 08:50:18.920  1468  1533 E ActivityManager:   9.7% 7499/com.google.android.talk: 7.2% user + 2.5% kernel / faults: 12414 minor 64 major
07-04 08:50:18.920  1468  1533 E ActivityManager: ANR in com.facebook.orca
07-04 08:50:18.920  1468  1533 E ActivityManager: pid: 5704
07-04 08:50:18.920  1468  1533 E ActivityManager:   4.9% 2462/com.google.process.gapps: 2.3% user + 2.5% kernel / faults: 1805 minor
07-04 08:50:18.920  1468  1533 E ActivityManager:   2.5% 582/surfaceflinger: 1.1% user + 1.3% kernel / faults: 14 minor 4 major
07-10 09:11:51.146  1571  1692 E ActivityManager: 20% TOTAL: 9.6% user + 10% kernel + 0.1% iowait + 0.5% irq + 0.2% softirq
     */


    public SystemFileHandler(Parser p) {
        super(p);
    }

    @Override
    public boolean process() {
        String pattern = ".*ActivityManager:\\s+([\\d.]+)%\\s+(\\d+)\\/(.*?):.*";
        BufferedReader br = null;

        try {
            br = new BufferedReader(new FileReader(fileName));
            String line;
            CpuUsageSnapshot cpuUsageSnapshot = null;
            Watchdog watchdog = null;
            while ((line = br.readLine()) != null) {
                showProgress();
                if (line.matches("(.*?)ActivityManager: CPU usage from \\d+ms to [-\\d]+ms (ago|later)+.*") ) {
                    cpuUsageSnapshot = new CpuUsageSnapshot(line);
                    cpuUsageSnapshot.addLine(line);
                    continue;
                }
                if (cpuUsageSnapshot != null) {

                    Matcher match = Pattern.compile(pattern).matcher(line);
                    if (match.find()) {
                        CpuUsageSnapshot.CpuUsage cpuUsage = new CpuUsageSnapshot.CpuUsage();
                        cpuUsage.percentage =Double.parseDouble(match.group(1));
                        cpuUsage.pid = Integer.parseInt(match.group(2));
                        cpuUsage.processName = match.group(3);
                        cpuUsageSnapshot.addCpuUsage(cpuUsage);
                        parser.addProcessRecord(cpuUsage.pid, cpuUsage.processName);
                        cpuUsageSnapshot.addLine(line);
                    } else {
                        parser.addCPUUsageSnapshot(cpuUsageSnapshot);
                        cpuUsageSnapshot = null;
                    }
                }

                if (line.matches(".*Watchdog: \\*\\*\\* WATCHDOG KILLING SYSTEM PROCESS:.*")) { //Caught watchdog
                    watchdog = new Watchdog(line);
                }

                if (watchdog != null) {
                    if (line.matches(".*Watchdog:.*")){
                        watchdog.addLine(line);
                    } else {
                        parser.addWatchdog(watchdog);
                        watchdog = null;
                    }
                }



                parser.saveLogByCrashPid(line);
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
