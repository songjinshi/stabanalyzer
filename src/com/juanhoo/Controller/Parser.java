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
    private static final int VALIDTIMERANGE = 60000;
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
        public String board = "";
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

    HashMap<Integer, CrashStack> crashStackMap = new HashMap<>();
    public static String DATAPATH;
    HashMap<Integer, String> processIdNameMap = new HashMap<>();
    HashMap<Integer, Process> processIdStackMap = new HashMap<Integer, Process>();
    PriorityQueue<Issue> issueQueue = new PriorityQueue<>();

    static HashMap<String, Process> sampleProcessMap = new HashMap<>();
    static HashMap<String, ThreadStack> runningBinderMap = new HashMap<>();
    private Triage trige;

    public void addCrashStack(CrashStack cs) {

        if (crashStackMap.get(cs.pid) == null) {
            crashStackMap.put(cs.pid, cs);
            issuePIDSet.add(cs.pid);
        }
    }


    public void addCPUUsageSnapshot(CpuUsageSnapshot cpuUsageSnapshot) {
        if (cpuUsageSnapshot != null) {
            cpuUsageSnapshots.add(cpuUsageSnapshot);
        }
    }

    public void setBuildFingerPrint(String data) {
        buildFingerPrint = data.replaceAll("\\\\","/");
    }

    public void addIssue(Issue issue) {

        for (Issue existedIssue:issueQueue) {
            if (issue.isSameIssue(existedIssue)) {
                if (!issue.buildFingerPrint.isEmpty() &&
                        existedIssue.buildFingerPrint.isEmpty()) {
                    existedIssue.buildFingerPrint = issue.buildFingerPrint;
                }
                return;
            }
            if (issue.isSameRootCauseIssue(existedIssue)) {
                existedIssue.sameIssueTime = issue.time;
            }
        }
        issueQueue.add(issue);
        if (issue.pid != -1) {
            issuePIDSet.add(issue.pid);
        }
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
        String prefixs[] = {"aplogcat-","logcat."};
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

    public boolean isThirdPartyProcess(String processName) {
        PackageInfo pkg = packageInfoMap.get(processName);
        if (pkg == null) {
            return false;
        }
        return pkg.isThirdPartyAPP();
    }

    public void process() {

        for (int i = LOGEND -1; i >= 0 ; i--) {
            if (logFiles[i] != null) {
                FileHandler fh = FileHandlerFactory.getLogFileHandle(this, i);
                if (fh != null) {
                    fh.open(logFiles[i]);
                    fh.process();
                }

            }
        }
        trige = new Triage(this);
        trige.addReferenceLogName(logFolder);

        int issueNum = issueQueue.size();
        if (issueNum == 0) {
            Triage.AnalysisComment analysisComment = new Triage.AnalysisComment();
            analysisComment.result = Triage.highlight("Not any tombstone/force close/anr/modem panic has been found in the log") + ", Please check the bug2go of the device.";
            if (!isValidLoginB2G()) {
                analysisComment.result ="The bug2go doesn't have valid logs. Please check and provide new bug2go! ";
            }
            trige.addReferenceLogName(logFolder);
            ArrayList<Triage.AnalysisComment>  comments = new ArrayList<>();
            comments.add(analysisComment);
            trige.addAnalysisResult(comments);
            trige.addNextStep("Move to product team to follow up with the user");
            return;
        }

        int ind = 0;

        Issue handleIssue = null;
        Issue previousIssue = null;

        while (!issueQueue.isEmpty()) {

            handleIssue = issueQueue.poll();

            if (previousIssue != null) {
                if (handleIssue.time != null &&
                        Math.abs(handleIssue.time.getTime() - previousIssue.time.getTime()) > 300000
                        || handleIssue.time == null) {
                    handleIssue = previousIssue;
                    break; //Don't handle the handleIssue happened before 5 mins
                }
            }

            previousIssue = handleIssue;
            ind++;
            if (handleIssue.issueType == Issue.IssueType.MODEMPANIC) {
                analyzeModemPanic(handleIssue, ind);
            }
            if (handleIssue.issueType == Issue.IssueType.TOMBSTONE) {
                analyzeTombstone(handleIssue, ind);
            }
            if (handleIssue.issueType == Issue.IssueType.ANR) {
                analyzeAnr(handleIssue, ind);
            }

            if (handleIssue.issueType == Issue.IssueType.FORCECLOSE) {
                analyzeCrash(handleIssue, ind);
            }

            if (handleIssue.issueType == Issue.IssueType.WATCHDOG) {
                analyzeWatchdog(handleIssue, ind);
            }

        }


        if (handleIssue != null) {
            if (ind > 1) {
                //At least two issues in the log
                String comment = "*All above issues maybe are related to "
                        + handleIssue.pName + " " +handleIssue.issueType.toString() +"*...";
                trige.addSimpleComment(comment);

            }
            String component;
            if (handleIssue.issueType != Issue.IssueType.MODEMPANIC) {
                String processName = (handleIssue.dependenPName == null) ? handleIssue.pName : handleIssue.dependenPName;
                component = trige.QueryAssignComponent(processName, handleIssue.keyWord);
            } else {
                String modemChip = deviceInfo.board.replace("msm", "Modem");
                component = modemChip + "-Panics";
            }
            String triageSolution = "Please "+ Triage.highlight(component) + " team have a further check.";
            trige.addNextStep(triageSolution);
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

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void analyzeWatchdog(Issue issue, int ind) {
        ArrayList<Triage.AnalysisComment>  analysisComments = new ArrayList<>();

        Triage.AnalysisComment analysisComment = new Triage.AnalysisComment();
        analysisComment.result += "* Issue "+ ind +": "+ issue.issueType.toString();
        analysisComment.result += "\nSystem server watchdog" + "*"+" (PID:"+issue.pid+") happened at " + issue.time;
        analysisComments.add(analysisComment);


        Watchdog watchdog = (Watchdog)issue;
        analysisComment = new Triage.AnalysisComment();
        analysisComment.result += "Reason: "+ watchdog.reason;
        analysisComment.referenceLog += watchdog.logData;
        analysisComments.add(analysisComment);

        for (int blockedThreadId = 0 ; blockedThreadId <  watchdog.blockedSysThreadNameList.size(); blockedThreadId++) {
            ArrayList<Triage.AnalysisComment> anrstackComments = getANRstackAnalysis(watchdog, watchdog.blockedSysThreadNameList.get(blockedThreadId));
            for (Triage.AnalysisComment comment:anrstackComments) {
                analysisComments.add(comment);
            }
        }

        trige.addAnalysisResult(analysisComments);
    }





    private void analyzeCrash(Issue issue, int ind) {
        ArrayList<Triage.AnalysisComment>  analysisComments = new ArrayList<>();

        Triage.AnalysisComment analysisComment ;

        String pkgVersion = null;
        PackageInfo pkg = packageInfoMap.get(issue.pName);
        if (pkg != null) {
            pkgVersion = pkg.getKey(PackageInfo.VERSION);
        }
        analysisComment = new Triage.AnalysisComment();
        analysisComment.result += "* Issue "+ ind +": "+ issue.issueType.toString();
        analysisComment.result += "\nForce close in process *"+ issue.pName + "*"+" (PID:"+issue.pid+") at " + issue.time;
        if (pkgVersion != null) {
            analysisComment.result += "  (Package Version : *"+ pkgVersion+"*) ";
            if (pkg.isThirdPartyAPP()) {
                analysisComment.result += "  Third-party application ";
            } else {
                analysisComment.result += "  Pre-install ";
            }

            if (pkg.isUpdated()) {
                analysisComment.result += "  Got updated ";
            }
        }

        analysisComment.result += "\n*Reason*: "+issue.reason;
        if (issue.sameIssueTime != null) {
            analysisComment.result += "\nThe crash is same with crash happend at"+ issue.sameIssueTime.toString();
        }
        analysisComment.result += "\n*Reference logs are below*:";
        analysisComment.referenceLog +=  issue.logData +"\n";
        CrashStack crashStack = crashStackMap.get(issue.pid);
        if (crashStack != null && issue.sameIssueTime == null) {
            analysisComment.referenceLog +=  crashStack.logData ;
            issue.keyWord = crashStack.keyWord;
        }
        analysisComments.add(analysisComment);
        //Reference log
        long crashTime = issue.time.getTime();
        String log = getLogByPid(issue.pid, crashTime - 300000, crashTime);
        if (log.length() > 0) {
            analysisComment = new Triage.AnalysisComment();
            analysisComment.hideLog = "The process log started from 5 mins ago:\n" + log ;
            analysisComments.add(analysisComment);
        }


        trige.addAnalysisResult(analysisComments);
        return ;
    }

    private void analyzeModemPanic(Issue issue, int i) {
        ArrayList<Triage.AnalysisComment>  analysisComments = new ArrayList<>();
        Triage.AnalysisComment analysisComment = new Triage.AnalysisComment();
        analysisComment.result = "\nIssue *" +i + ": " + issue.issueType.toString() +"*";
        analysisComments.add(analysisComment);

        analysisComment = new Triage.AnalysisComment();
        analysisComment.result += issue.issueType.toString() + " happened at " + issue.time +"\n";
        analysisComment.result += "*Reason*: "+issue.reason;

        if (issue.logData.length() != 0) {
            analysisComment.result += "*Reference logs are below*:\n";
            analysisComment.referenceLog += issue.logData + "\n";
        }
        analysisComments.add(analysisComment);

        trige.addAnalysisResult(analysisComments);
        return ;
    }

    private ArrayList<Triage.AnalysisComment> getANRstackAnalysis(Issue issue,  String tName) {

        Process ps = processIdStackMap.get(issue.pid);
        if (ps == null) {
            return new ArrayList<>();
        }
        int tid = ps.getThreadByName(tName).get(0).tid;

        return analyzeANRTrace(issue, tid);
    }

    private ArrayList<Triage.AnalysisComment> analyzeANRTrace(Issue issue, int tid) {
        ArrayList<Triage.AnalysisComment> comments = new ArrayList<>();
        Triage.AnalysisComment analysisComment = new Triage.AnalysisComment();
        Process ps = processIdStackMap.get(issue.pid);
        if (ps == null) {
            analysisComment.result = "*ANR Trace* No corresponding ANR trace file";
            comments.add(analysisComment);
        } else {
            if (!ps.hasDeadLock()) {
                ThreadStack ts = ps.getThreadByID(tid);
                comments = ps.getStackInfo(ts);
                String binderFunc = ps.getBlockedFunc(ps.getThreadByID(tid));
                if (binderFunc != null && !binderFunc.isEmpty()) {
                    System.out.println("Check binder function:" + binderFunc);
                    ThreadStack binderTS = runningBinderMap.get(binderFunc);
                    if (binderTS != null && binderTS.parent.pid != issue.pid) {
                        analysisComment = new Triage.AnalysisComment();
                        analysisComment.result = "It maybe waiting on transaction running in process " + trige.highlight(binderTS.parent.name)
                                + " (thread "+ binderTS.tid + ")";
                        analysisComment.referenceLog = binderTS.toString();
                        issue.dependenPName = binderTS.parent.name;
                        //issue.keyWord = "";
                        comments.add(analysisComment);
                    }
                } else if (ts.isTimeCounsmedLoad()) {
                    analysisComment = new Triage.AnalysisComment();
                    analysisComment.result = "Looks like the thread has *time-consumed* work.";
                    comments.add(analysisComment);

                }
                //


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

    private String analyzeAnr(Issue issue, int ind) {
        String res = "";
        ArrayList<Triage.AnalysisComment>  analysisComments = new ArrayList<>();
        trige.addReferenceLogName(logFolder);
        Triage.AnalysisComment analysisComment = new Triage.AnalysisComment();
        analysisComment.result = "* Issue " +ind  + ":" + issue.issueType.toString();
        String pkgVersion = null;
        PackageInfo pkg = getPackage(issue.pName);
        if (pkg != null) {
            pkgVersion = pkg.getKey(PackageInfo.VERSION);
        }
        analysisComment.result += "\nProcess *"+ issue.pName + "*"+"(PID:"+issue.pid+") happened at " + issue.time ;
        if (pkgVersion != null) {
            analysisComment.result += "  (Package Version : *"+ pkgVersion+"*) ";
            if (pkg.isThirdPartyAPP()) {
                analysisComment.result += "  Third-party application ";
            } else {
                analysisComment.result += "  Pre-install ";
            }

            if (pkg.isUpdated()) {
                analysisComment.result += "  Got updated ";
            }
        }
        analysisComment.result += "\nReason: " + issue.reason;
        analysisComment.referenceLog += issue.logData;
        analysisComments.add(analysisComment);
        //Analyze anr
        ArrayList<Triage.AnalysisComment> anrstackComments = analyzeANRTrace(issue, MAINTHREADID);
        for (Triage.AnalysisComment comment:anrstackComments) {
            analysisComments.add(comment);
        }
        ArrayList<CpuUsageSnapshot.CpuUsage> cpuUsages = getCpuUsagesByLoad(issue.time, 30);
        for (CpuUsageSnapshot.CpuUsage cpuUsageSnapshot:cpuUsages) {
            analysisComment = new Triage.AnalysisComment();
            analysisComment.result = " *Top CPU usage application: *"+ cpuUsageSnapshot.processName +"* (PID:"+
                    cpuUsageSnapshot.pid+") CPU usage:"+cpuUsageSnapshot.percentage;
            analysisComments.add(analysisComment);
        }

        long anrTime = issue.time.getTime();
        String log = getLogByPid(issue.pid, anrTime - 300000, anrTime);
        if (log.length() > 0) {
            analysisComment = new Triage.AnalysisComment();
            analysisComment.hideLog = "The process log started from 5 mins ago:\n" + log ;
            analysisComments.add(analysisComment);
        }

        trige.addAnalysisResult(analysisComments);

        return res;
    }

    private void analyzeTombstone(Issue issue, int ind) {
        ArrayList<Triage.AnalysisComment>  analysisComments = new ArrayList<>();
        trige.addReferenceLogName(logFolder);
        Triage.AnalysisComment analysisComment = new Triage.AnalysisComment();
        analysisComment.result += "* Issue "+ ind +": "+issue.issueType.toString();
        if (issue.buildFingerPrint.compareTo(buildFingerPrint) == 0) {
            analysisComment.result += "\nTombstone in process *"+ issue.pName + "*"+"(PID:"+issue.pid+") Thread name: "+issue.tName+ "(TID:" +issue.tid+" ) at " + issue.time;
            analysisComment.result += "\nReason: "+issue.reason;
            analysisComment.referenceLog += issue.logData;
            analysisComments.add(analysisComment);
            if (issue.time == null) {
                trige.addAnalysisResult(analysisComments);
                return;
            }
            long tombstonetime = issue.time.getTime();
            String log = getLogByPid(issue.pid, tombstonetime - 300000, tombstonetime);
            if (log.length() > 0) {
                analysisComment = new Triage.AnalysisComment();
                analysisComment.hideLog = "The process log started from 5 mins ago:\n" + log ;
                analysisComments.add(analysisComment);
            }

        } else {

            analysisComment.result += "One tombstone happened in different version.\n The tombstone happened at sw version: "+ issue.buildFingerPrint +"\n";
            analysisComment.result += "Current software version is: "+ buildFingerPrint +"\n";
            analysisComment.result += "Tombstone in process *"+ issue.pName + "*"+"(PID:"+issue.pid+") Thread name: "+issue.tName+ "(TID:" +issue.tid+" )";
            analysisComment.result += "\nReason: "+issue.reason;
            analysisComment.referenceLog += issue.logData ;
            analysisComments.add(analysisComment);
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

    public String getJiraComments() {
        return trige.getComments();

    }

    public String getHtmlComments() {
        return trige.getHtmlFormatComments();
    }


}
