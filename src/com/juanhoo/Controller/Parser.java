package com.juanhoo.Controller;

import com.juanhoo.Utils.LogItem;
import com.juanhoo.Utils.MergeQueue;
import com.juanhoo.Utils.Tools;
import com.juanhoo.Utils.Triage;
import com.juanhoo.file.FileHandler;
import com.juanhoo.file.FileHandlerFactory;

import java.io.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by JH on 2016/7/6.
 */
//Tombstone is special, for dameon tombstone, doesn't shown in main/event/system log. Maybe in crash.txt, we need check entry.txt and dropbox.txt
public final class Parser {
    private final String BEGINLABEL = "----- pid";
    private final String ENDLABEL = "----- end ";
    private final String BUGREPORTTRACEBEGIN = "------ VM TRACES JUST NOW";
    private String logFolder;
    private Date startTime; //**RIL Daemon Started**
    private Date stopTime; //GsmSST  : NITZ: after Setting time of day
    private static final int VALIDTIMERANGE = 10000;
    public String buildFingerPrint = null;
    public HashMap<String, PackageInfo> packageInfoMap = new HashMap<>();
    public HashMap<Integer, MergeQueue> logByPidMap = new HashMap<>();
    public HashSet<Integer> issuePIDSet = new HashSet<>();

    public static class UserInfo {
        public String userCoreID ;
        public String userEmail ;

    }

    public static class DeviceInfo {
        public String hwVer =""; //
        public String swVer ="";
        public String model =""; //XT1563
        public String swType =""; //"TYPE": "userdebug"
        public String issueSummary = ""; //"summary": "SYSTEM_TOMBSTONE",
        public String buildID = "";   //MPN24.59
        public String bpVer = "";

    }


    private UserInfo userInfo;
    private DeviceInfo deviceInfo;

    public static final int KERNELLOG = 0;
    public static final int SYSTEMLOG = 1;
    public static final int MAINLOG = 2;
    public static final int CRASHLOG = 3;
    public static final int RADIOLOG = 4;
    public static final int EVENTLOG = 5;
    public static final int BUGREPORTLOG = 6;
    public static final int TRACELOG = 7;
    public static final int ENTRYLOG = 8;
    public static final int REPORTINFOLOG = 9;
    public static final int PROCESSLIST = 10;
    public static final int LOGEND = 11;

    private final static String outputFileName = "analysisresult.html";

    private static final String[] logNameKeys = new String[]{
            "kernel",
            "system",
            "main",
            "crash",
            "radio",
            "events",
            "bugreport",
            "data_anr_traces.txt",
            "entry.txt",
            "report_info.txt",
            "Process_list.txt"
    };


    private ArrayList<CpuUsageSnapshot> cpuUsageSnapshots = new ArrayList<>();
    private String[] logFiles ;
    ArrayList<ANR> anrList = new ArrayList<>();
    ArrayList<Crash> crashList = new ArrayList<>();
    HashMap<Integer, CrashStack> crashStackMap = new HashMap<>();
    ArrayList<Tombstone> tombstoneList = new ArrayList<>();
    ArrayList<ModemPanic> modemPanicList = new ArrayList<>();
    ArrayList<Watchdog> watchdogList = new ArrayList<>();
    public static String DATAPATH;
    HashMap<Integer, String> processIdNameMap = new HashMap<>();
    HashMap<Integer, Process> processIdStackMap = new HashMap<Integer, Process>();
/*    public static Parser getInstance() {
        return INSTANCE ;
    }*/
    static HashMap<String, Process> sampleProcessMap = new HashMap<>();
    static HashMap<String, ThreadStack> runningBinderMap = new HashMap<>();
    private Triage trige;

    public void addCrashStack(CrashStack cs) {

        if (crashStackMap.get(cs.pid) == null) {
            crashStackMap.put(cs.pid, cs);
            issuePIDSet.add(cs.pid);
        }
    }

    public void addWatchdog(Watchdog watchdog) {
        watchdogList.add(watchdog);
    }

    public void addCPUUsageSnapshot(CpuUsageSnapshot cpuUsageSnapshot) {
        if (cpuUsageSnapshot != null) {
            cpuUsageSnapshots.add(cpuUsageSnapshot);
        }
    }
    public void addTombstone(Tombstone tombstone) {
        for (Tombstone existedTombstone:tombstoneList) {
            if (tombstone.isDup(existedTombstone)) {

                if (existedTombstone.buildFingerPrint.isEmpty()) {
                    existedTombstone.buildFingerPrint = tombstone.buildFingerPrint;
                }
                if (existedTombstone.time == null) {
                    existedTombstone.time = tombstone.time;
                }
                return;
            }
        }
        issuePIDSet.add(tombstone.pid);
        tombstoneList.add(tombstone);
    }

    public void addModemPanic(ModemPanic mp) {
        modemPanicList.add(mp);
    }
    public void setBuildFingerPrint(String data) {
        buildFingerPrint = data.replaceAll("\\\\","/");
    }

    public void addANR(ANR anr) {
        anrList.add(anr);
        issuePIDSet.add(anr.pid);
    }

    public void addNewCrash(Crash crash) {
        int newCrashPos = crashList.size();
        for (int i = 0; i < crashList.size(); i++) {
            Crash existedCrash = crashList.get(i);
            if (existedCrash.reason.compareTo(crash.reason) == 0) {
                existedCrash.dupInd = newCrashPos;
            }
        }
        issuePIDSet.add(crash.pid);
        crashList.add(crash);
    }


    public void addLog(int pid, long t, String line){
        MergeQueue logQueue = logByPidMap.get(pid);
        if (logQueue == null) {
            logQueue = new MergeQueue();
            logByPidMap.put(pid, logQueue);
        }
        logQueue.add(t, line);
    }

    public void setDeviceInfo(DeviceInfo info) {
        deviceInfo = info;
    }

    public void setUserInfo (UserInfo info) {
        userInfo = info;
    }

    private void clearHistory() {
        anrList.clear();
        crashList.clear();
        tombstoneList.clear();
        crashStackMap.clear();
        processIdNameMap.clear();
        processIdStackMap.clear();
        runningBinderMap.clear();
        cpuUsageSnapshots.clear();
    }

    public void extractPID(String line) {
        String pattern = ".+am_proc_bound:\\s+\\[(\\d+),(\\d+),(.*?)\\]";
        Matcher match = Pattern.compile(pattern).matcher(line);
        if (match.find()) {
            Integer pid = Integer.parseInt(match.group(2));
            String processName = match.group(3);
            addProcessRecord(pid, processName);
        }
    }

    public void addProcessRecord(Integer pid, String processName) {
        if (processIdNameMap.get(pid) == null) {
            processIdNameMap.put(pid, processName);
        }
    }

    public void addProcessStack(Process ps) {
        if (processIdStackMap.get(ps.pid) == null) {
            processIdStackMap.put(ps.pid, ps);
        }
    }

    public void saveLogByCrashPid(String line) {
        String pattern =  "^(\\d{1,2})-(\\d{1,2})\\s+(\\d{1,2}):(\\d{1,2}):(\\d{1,2}).(\\d{1,3})\\s+(\\d{1,5})\\s+(\\d{1,5}).*";
        Matcher match = Pattern.compile(pattern).matcher(line);
        if (match.find()) {
            int pid = Integer.parseInt(match.group(7));
            int tid = Integer.parseInt(match.group(8));
            String crashTime = "2016-"+match.group(1)+"-"+match.group(2)+" "+match.group(3)+":"+match.group(4)+":"+match.group(5)+"."+match.group(6);
            DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH);
            long time = -1;
            try {
                time = format.parse(crashTime).getTime();
            } catch (ParseException e) {
                e.printStackTrace();
            }
            if (issuePIDSet.contains(pid)) {
                addLog(pid, time, line);
            }
        }
    }


    public String getLogByPid(int pid, long startTime, long endTime) {
        MergeQueue logQueue = logByPidMap.get(pid);
        String logs = "";
        if (logQueue == null) {
            return "";
        }
        while (!logQueue.isEmpty()) {
            LogItem log = logQueue.poll();
            //if (log.time < af)
            System.out.print(".");
            if (log.time > startTime && log.time < endTime) {
                logs += log.data +"\n";
            }
        }

        return logs;
    }

    public ArrayList<CpuUsageSnapshot.CpuUsage> getCpuUsagesByLoad(Date time, float cpuLoad) {
        ArrayList<CpuUsageSnapshot.CpuUsage> cpuUsages = new ArrayList<>();

        for (CpuUsageSnapshot cpuUsageSnapshot: cpuUsageSnapshots) {
            if (Math.abs(cpuUsageSnapshot.time.getTime() - time.getTime()) > VALIDTIMERANGE) {
                continue;
            }
            cpuUsages.addAll(cpuUsageSnapshot.cpuUsages.stream().filter(cpuUsage -> cpuUsage.percentage > cpuLoad).collect(Collectors.toList()));
        }
        return cpuUsages;
    }

    private boolean isValidLoginB2G() {
        for (int i = KERNELLOG; i < TRACELOG; i++) {
            if (logFiles[i] != null) {
                return true;
            }
        }
        return false;
    }


    public Parser() {
        Map<String, String> env = System.getenv();
        for (String envName : env.keySet()) {

            if (envName.contains("ParsePath")) {
                DATAPATH = env.get(envName);
                break;
            }
        }

    }

    public void setLogFolder(String folder) {
        logFolder = folder;
        generateLogFileMap();
    }



    //Analyze Bug2Go folder to intialize file log name
    private void generateLogFileMap() {
        String prefixs[] = {"logcat.","aplogcat-"};
        logFiles = new String[LOGEND];

        //Bugreport
        logFiles[BUGREPORTLOG] = Tools.getBugreportFileName(logFolder,"bugreport");
        for (int i = KERNELLOG; i <= EVENTLOG; i++) {
            for (String prefix:prefixs) {
                String fileName = logFolder + File.separator + prefix + logNameKeys[i] + ".txt";
                File file = new File(fileName);

                if (file.exists() && file.isFile()) {
                    logFiles[i] = fileName;  //Find file and store in corresponding position.
                    if (i != CRASHLOG) {  //For crash log, looks like logcat.crash.txt has more information
                        break;
                    }
                }

            }
            //TODO  Remove it after implement bugreportfilehandle
            if (logFiles[i] == null) {
                logFiles[i] = logFiles[BUGREPORTLOG];
            }

        }

        for (int i = TRACELOG;i < LOGEND; i++) {
            String fileName = logFolder + File.separator + logNameKeys[i];
            File file = new File(fileName);
            if (file.exists() && file.isFile()) {
                logFiles[i] = fileName;  //Find file and store in corresponding position.
            }
        }

       // logFiles[TRACELOG] == null?Tools.getBugreportFileName(logFolder, "data_anr_traces_"):
        //If we couldn't find trace file, try to use bugreport.txt to instead
        if (logFiles[TRACELOG] == null) {
            String fileName = Tools.getBugreportFileName(logFolder, "data_anr_traces_");
            logFiles[TRACELOG] = (fileName != null)?fileName
                    :logFiles[BUGREPORTLOG];
        }
    }



    public static void addRunningBinder(String func, ThreadStack ts) {
        runningBinderMap.put(func, ts);
    }

    static public ArrayList<ThreadStack> getSampleThreadStack(String processName, String threadName) {

        ArrayList<ThreadStack> stackList = new ArrayList<>();

        if (sampleProcessMap.size() == 0) {
            System.out.println("Sample stack hasn't been created!");
            return stackList;
        }

        if (sampleProcessMap.get(processName) != null) {
            stackList = sampleProcessMap.get(processName).getThreadByName(threadName);
        }
        return stackList;
    }

    public void process() {
        clearHistory();

        for (int i = LOGEND -1; i >= 0 ; i--) {
            if (logFiles[i] != null) {
                FileHandler fh = FileHandlerFactory.getLogFileHandle(this, i);
                if (fh != null) {
                    fh.open(logFiles[i]);
                    fh.process();
                }

            }
        }
        trige = new Triage();

        int issueNum = anrList.size() + tombstoneList.size() +
                crashList.size() +modemPanicList.size()
                +watchdogList.size();
        if (issueNum == 0) {
            Triage.AnalysisComment analysisComment = new Triage.AnalysisComment();
            analysisComment.result ="*Not any tombstone/force close/anr/modem panic has been found in the log*.";
            if (!isValidLoginB2G()) {
                analysisComment.result ="The bug2go doesn't have valid logs. Please check provide new bug2go! ";
            }
            trige.addReferenceLogName(logFolder);
            ArrayList<Triage.AnalysisComment>  comments = new ArrayList<>();
            comments.add(analysisComment);
            trige.addAnalysisResult(comments);
        }


        if (anrList.size() != 0) {
            analyzeAnr();
        }

        if (tombstoneList.size() != 0) {
            analyzeTombstone();
        }

        if (crashList.size() != 0) {
            analyzeCrash();
        }

        if (modemPanicList.size()!=0) {
            analyzeModemPanic();
        }

        if (watchdogList.size() != 0) {
            analyzeWatchdog();
        }


        trige.generateOutput(logFolder+File.separator+ outputFileName);

    }


    public static boolean unZIPFile(String zipFileName, String destDirName) {

        int BUFFER_SIZE = 4096;
        try {
            ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFileName));
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                String filePath = destDirName + File.separator + entry.getName();
                File file = new File(filePath);
                if (filePath.endsWith("/")) {
                    file.mkdirs();
                    entry = zis.getNextEntry();
                    continue;
                }
                File parent = file.getParentFile();
                if (parent != null) {
                    parent.mkdir();
                }

                if (!entry.isDirectory()) {
                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
                    byte[] bytesIn = new byte[BUFFER_SIZE];
                    int read = 0;
                    System.out.print(".");
                    while ((read = zis.read(bytesIn)) != -1) {
                        bos.write(bytesIn, 0, read);
                    }
                    bos.close();

                } else {
                    File dir = new File(filePath);
                    dir.mkdir();
                }
                zis.closeEntry();
                entry = zis.getNextEntry();

            }
            System.out.println();

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void analyzeWatchdog() {
        ArrayList<Triage.AnalysisComment>  analysisComments = new ArrayList<>();
        trige.addReferenceLogName(logFolder);
        Triage.AnalysisComment analysisComment = new Triage.AnalysisComment();
        analysisComment.result = "\nIn the log, total *"+watchdogList.size() + " watchdog reset*\n";
        analysisComments.add(analysisComment);

        for (int i = 0; i < watchdogList.size(); i++) {
            Watchdog watchdog = watchdogList.get(i);
            analysisComment = new Triage.AnalysisComment();
            analysisComment.result += "* *System_server Watchdog "+ (i+1) +"\n";
            analysisComment.result += "Happened at "+ watchdog.time + "\n";
            analysisComment.result += "Reason: "+ watchdog.reason;
            analysisComment.referenceLog += watchdog.logData;
            analysisComments.add(analysisComment);

            for (int blockedThreadId = 0 ; blockedThreadId <  watchdog.blockedSysThreadNameList.size(); blockedThreadId++) {
                ArrayList<Triage.AnalysisComment> anrstackComments = getANRstackAnalysis(watchdog.pid, watchdog.blockedSysThreadNameList.get(blockedThreadId));
                for (Triage.AnalysisComment comment:anrstackComments) {
                    analysisComments.add(comment);
                }
            }
        }
        trige.addAnalysisResult(analysisComments);
    }



    private void analyzeCrash() {
        ArrayList<Triage.AnalysisComment>  analysisComments = new ArrayList<>();
        trige.addReferenceLogName(logFolder);
        Triage.AnalysisComment analysisComment = new Triage.AnalysisComment();
        analysisComment.result = "\nIn the log, total *"+crashList.size() + " force close*";
        analysisComments.add(analysisComment);

        for (int i = 0; i < crashList.size(); i++) {
            Crash crash = crashList.get(i);
            CrashStack crashStack = crashStackMap.get(crash.pid);
            String pkgVersion = null;
            PackageInfo pkg = packageInfoMap.get(crash.name);
            if (pkg != null) {
                pkgVersion = pkg.getKey(PackageInfo.VERSION);
            }
            analysisComment = new Triage.AnalysisComment();
            analysisComment.result += "* *Force close "+ (i+1) +"\n";
            analysisComment.result += "Force close in process *"+ crash.name + "*"+" (PID:"+crash.pid+") at " + crash.time;
            if (pkgVersion != null) {
                analysisComment.result += "  (Package Version : *"+ pkgVersion+"*) ";
            }

            analysisComment.result += "\n*Reason*: "+crash.reason;
            if (crash.dupInd != -1) {
                analysisComment.result += "\nThe crash is same with crash"+ (crash.dupInd + 1);
            }
            analysisComment.result += "\n*Reference logs are below*:";
            analysisComment.referenceLog +=  crash.logData +"\n\n";
            if (crashStack != null && crash.dupInd == -1) {
                analysisComment.referenceLog +=  crashStack.logData ;
            }
            analysisComments.add(analysisComment);
            //Reference log
            long crashTime = crash.time.getTime();
            String log = getLogByPid(crash.pid, crashTime - 300000, crashTime);
            if (log.length() > 0) {
                analysisComment = new Triage.AnalysisComment();
                analysisComment.hideLog = "The process log started from 5 mins ago:\n" + log ;
                analysisComments.add(analysisComment);
            }

        }
        trige.addAnalysisResult(analysisComments);
        return ;
    }

    private void analyzeModemPanic() {
        ArrayList<Triage.AnalysisComment>  analysisComments = new ArrayList<>();
        trige.addReferenceLogName(logFolder);
        Triage.AnalysisComment analysisComment = new Triage.AnalysisComment();
        analysisComment.result = "\nIn the log, total *"+modemPanicList.size() + " modem panic*";
        analysisComments.add(analysisComment);

        for (int i = 0; i < modemPanicList.size(); i++) {
            ModemPanic modemPanic = modemPanicList.get(i);
            analysisComment = new Triage.AnalysisComment();
            analysisComment.result += "* Modem panic "+ (i+1) +"\n";
            analysisComment.result += "Modem panic happened at " + modemPanic.time +"\n";
            analysisComment.result += "*Reason*: "+modemPanic.reason;

            if (modemPanic.logData.length() != 0) {
                analysisComment.result += "*Reference logs are below*:\n";
                analysisComment.referenceLog += modemPanic.logData + "\n";
            }
            analysisComments.add(analysisComment);
        }
        trige.addAnalysisResult(analysisComments);
        return ;
    }




    private ArrayList<Triage.AnalysisComment> getANRstackAnalysis(int pid, String tName) {

        Process ps = processIdStackMap.get(pid);
        if (ps == null) {
            return new ArrayList<>();
        }
        int tid = ps.getThreadByName(tName).get(0).tid;

        return getANRstackAnalysis(pid, tid);
    }

    private ArrayList<Triage.AnalysisComment> getANRstackAnalysis(int pid, int tid) {
        ArrayList<Triage.AnalysisComment> comments = new ArrayList<>();
        Triage.AnalysisComment analysisComment = new Triage.AnalysisComment();
        Process ps = processIdStackMap.get(pid);
        if (ps == null) {
            analysisComment.result = "*ANR Trace* No corresponding ANR trace file";
            comments.add(analysisComment);
        } else {
            if (!ps.hasDeadLock()) {
                comments = ps.getStackInfo(ps.getThreadByID(tid));
                String binderFunc = ps.getBlockedFunc(ps.getThreadByID(tid));
                if (binderFunc != null) {
                    System.out.println("Check binder function:" + binderFunc);
                }
                ThreadStack binderTS = runningBinderMap.get(binderFunc);
                if (binderTS != null) {
                    analysisComment = new Triage.AnalysisComment();
                    analysisComment.result = "It maybe waiting on transaction running in process " + binderTS.parent.name
                            + "thread "+ binderTS.tid;
                    analysisComment.referenceLog = binderTS.toString();
                    comments.add(analysisComment);
                }
            } else {
                comments = ps.getDeadLockDetail();
                return comments;
            }
        }
        return comments;
    }


    private PackageInfo getPackage(String name) {
        PackageInfo pkg = null;
        int index = 0;
        do{
            pkg = packageInfoMap.get(name);
            if (pkg != null) {
                return pkg;
            }
            index = name.lastIndexOf(".");
            if (index == -1) {
                break;
            }
            name = name.substring(0, index);
        } while (true);
        return pkg;
    }


    private static int MAINTHREADID = 1;

    private String analyzeAnr() {
        String res = "";
        ArrayList<Triage.AnalysisComment>  analysisComments = new ArrayList<>();
        trige.addReferenceLogName(logFolder);
        Triage.AnalysisComment analysisComment = new Triage.AnalysisComment();
        analysisComment.result = "\nIn the log, total *"+anrList.size() + "* ANRs\n";
        analysisComments.add(analysisComment);
        for (int i = 0; i < anrList.size(); i++) {
            ANR anr = anrList.get(i);
            analysisComment = new Triage.AnalysisComment();
            analysisComment.result += "* ANR "+ (i+1) +"\n";
            String pkgVersion = null;
            PackageInfo pkg = getPackage(anr.name);
            if (pkg != null) {
                pkgVersion = pkg.getKey(PackageInfo.VERSION);
            }
            analysisComment.result += "Process *"+ anr.name + "*"+"(PID:"+anr.pid+") happened at " + anr.time ;
            if (pkgVersion != null) {
                analysisComment.result += " (Package Version: *"+pkgVersion+ "* )";
            }
            analysisComment.result += "\nReason: " + anr.reason;
            analysisComment.referenceLog += anr.logData;
            analysisComments.add(analysisComment);
            //Analyze anr
            ArrayList<Triage.AnalysisComment> anrstackComments = getANRstackAnalysis(anr.pid, MAINTHREADID);
            for (Triage.AnalysisComment comment:anrstackComments) {
                analysisComments.add(comment);
            }
            ArrayList<CpuUsageSnapshot.CpuUsage> cpuUsages = getCpuUsagesByLoad(anr.time, 30);
            for (CpuUsageSnapshot.CpuUsage cpuUsageSnapshot:cpuUsages) {
                analysisComment = new Triage.AnalysisComment();
                analysisComment.result = " *Top CPU usage application: *"+ cpuUsageSnapshot.processName +"* (PID:"+
                        cpuUsageSnapshot.pid+") CPU usage:"+cpuUsageSnapshot.percentage;
                analysisComments.add(analysisComment);
            }

            long anrTime = anr.time.getTime();
            String log = getLogByPid(anr.pid, anrTime - 300000, anrTime);
            if (log.length() > 0) {
                analysisComment = new Triage.AnalysisComment();
                analysisComment.hideLog = "The process log started from 5 mins ago:\n" + log ;
                analysisComments.add(analysisComment);
            }
        }
        trige.addAnalysisResult(analysisComments);

        return res;
    }

    private void analyzeTombstone() {
        ArrayList<Triage.AnalysisComment>  analysisComments = new ArrayList<>();
        trige.addReferenceLogName(logFolder);
        Triage.AnalysisComment analysisComment = new Triage.AnalysisComment();
        analysisComment.result = "\nIn the log, total *"+tombstoneList.size() + "* tombstones";
        analysisComments.add(analysisComment);
        for (int i = 0; i < tombstoneList.size(); i++) {
            Tombstone ts = tombstoneList.get(i);
            analysisComment = new Triage.AnalysisComment();
            analysisComment.result += "* Tombstone "+ (i+1) +"\n";
            if (ts.buildFingerPrint.compareTo(buildFingerPrint) == 0) {
                analysisComment.result += "Tombstone in process *"+ ts.pName + "*"+"(PID:"+ts.pid+") Thread name: "+ts.tName+ "(TID:" +ts.tid+" ) at " + ts.time;
                analysisComment.result += "\nReason: "+ts.reason;
                analysisComment.referenceLog += ts.logData;
                analysisComments.add(analysisComment);
                if (ts.time == null) {
                    continue;
                }
                long tombstonetime = ts.time.getTime();
                String log = getLogByPid(ts.pid, tombstonetime - 300000, tombstonetime);
                if (log.length() > 0) {
                    analysisComment = new Triage.AnalysisComment();
                    analysisComment.hideLog = "The process log started from 5 mins ago:\n" + log ;
                    analysisComments.add(analysisComment);
                }

            } else {

                analysisComment.result += "One tombstone happened in different version.\n The tombstone happened at sw version: "+ ts.buildFingerPrint +"\n";
                analysisComment.result += "Current software version is: "+ buildFingerPrint +"\n";
                analysisComment.result += "Tombstone in process *"+ ts.pName + "*"+"(PID:"+ts.pid+") Thread name: "+ts.tName+ "(TID:" +ts.tid+" )";
                analysisComment.result += "\nReason: "+ts.reason;
                analysisComment.referenceLog += ts.logData ;
                analysisComments.add(analysisComment);
            }
        }
        trige.addAnalysisResult(analysisComments);
    }


    private ThreadStack getMainThreadByPid(Date time, int pid) {

        Process ps = processIdStackMap.get(pid);
        if (ps != null) {
            if (Math.abs(ps.snapshotTime.getTime() - time.getTime()) < VALIDTIMERANGE) {
                return ps.getMainThread();
            }
        }
        return null;
    }







    private void debugFilesInBug2Go() {
        System.out.println("\n-------File List Start-----------");
        for (String file:logFiles) {
            System.out.println("---"+file);
        }
        System.out.println("\n-------File List End-----------");
    }

    private void printPIDHistory() {
        System.out.println("----------------Process ID/Name List-----------------------------");
        for (Integer pid:processIdNameMap.keySet()) {
            String processName = processIdNameMap.get(pid);
            System.out.println("pid: "+pid+" Process Name:"+processName);
        }
        System.out.println("-----------------End------------------------------------------------");
    }

    private void printAnalyzeResult() {

        System.out.println("In the bug2go total "+crashList.size() + " force close! ");

        System.out.println("In the bug2go total "+anrList.size() + " ANR!");
        for (ANR anr:anrList) {
            System.out.println(anr.name);
        }


        System.out.println("In the bug2go total "+crashStackMap.size()+" stack in thez force close!");
        for (Integer pid:crashStackMap.keySet()) {
            CrashStack cs = crashStackMap.get(pid);
            System.out.println("Crash process "+ pid + " start -----------------------------");
            System.out.println(cs);
            System.out.println("Crash " + " end -----------------------------");
        }

        System.out.println("In the bug2go total "+tombstoneList.size()+" tombstone!");

        for (int i =0; i<tombstoneList.size(); i++) {
            Tombstone ts = tombstoneList.get(i);
            System.out.println("Process ID:"+ts.pid + " Process name: "+ ts.pName);
            System.out.println("Tombstone "+i+ " start -----------------------------");
            System.out.println(ts);
            System.out.println("Tombstone "+i+ " end -----------------------------");
        }

       /* for (ANR anr:anrList) {
            System.out.println(anr.logData);
            String res = analyzeAnr(anr.pid);
            if (!res.isEmpty()) {
                System.out.println(res);
            }
        }*/
    }

    private void printUserInfo() {
        if (userInfo == null) {
            System.out.println("No valid user info");
            return;
        }
        System.out.println("Issue submitted by user "+userInfo.userCoreID + " Email:" +userInfo.userEmail);
    }

    private void printDeviceInfo() {
        if (deviceInfo == null) {
            System.out.println("No valid device info");
            return;
        }
        System.out.println("Device Model :"+deviceInfo.model + " Build ID:" +deviceInfo.buildID +"\nbuildfingerprint: "+ buildFingerPrint);
    }

    private void printBlockedMainThreadStack() {
        for (Integer pid:processIdStackMap.keySet()) {
            Process ps = processIdStackMap.get(pid);
            if (ps.isMainThreadBlocked()) {
                System.out.println("Process name " + processIdNameMap.get(ps.pid));
                ThreadStack ts = ps.getMainThread();
                System.out.println(ts);
            }
        }
    }

    private void printRunningBinderThreadStack() {
        for (Integer pid:processIdStackMap.keySet()) {
            Process ps = processIdStackMap.get(pid);
            ArrayList<ThreadStack> threadStacks = ps.getBlockedBinderThread();
            if (threadStacks.size()!= 0) {
                System.out.println("------------Process "+ps.name+"-----------------");
            }
            for (ThreadStack ts:threadStacks) {
                System.out.println(ts);
            }
        }
    }




    private void printTopCPUusage() {
        for (ANR anr:anrList) {
            ArrayList<CpuUsageSnapshot.CpuUsage> cpuUsages = getCpuUsagesByLoad(anr.time, 5);
            System.out.println("High CPU up 5% ---------");
            for (CpuUsageSnapshot.CpuUsage cpuUsage:cpuUsages) {
                System.out.println("Pid = "+ cpuUsage.pid +" name: "+cpuUsage.processName + " offload:"+cpuUsage.percentage+"%");
               ThreadStack ts= getMainThreadByPid(anr.time, cpuUsage.pid);
                if (ts != null) {
                    System.out.println(ts);
                }
            }
        }
    }


    public void debugOutput() {
        System.out.println("\n================================="+logFolder+" Start =======================================\n");
        debugFilesInBug2Go();
        System.out.println("\n---------------------------------Analysis Result----------------------------------------------");
        printAnalyzeResult();
      //  printPIDHistory();
      //  printBlockedMainThreadStack();
        printRunningBinderThreadStack();
       // printTopCPUusage();
        printUserInfo();
        printDeviceInfo();
        System.out.println("\n================================="+logFolder+" End =======================================\n");
    }

}
