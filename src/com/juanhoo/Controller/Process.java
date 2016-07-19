package com.juanhoo.Controller;

import com.juanhoo.Utils.Triage;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Yi He on 7/7/2016.
 *
 */
public class Process {
    public  int pid = -1;
    public String name;
    public Date snapshotTime;
    private int mainThreadID = -1;
    boolean hasDeadlock = false;
    public ArrayList<Triage.AnalysisComment> deadLockDetail = new ArrayList<>();
    HashMap<Integer, ThreadStack> threadStackMapByTID = new HashMap<>();
    HashMap<String, ArrayList<ThreadStack>>  threadMapByName = new HashMap<>();
    //Pattern to
    String patternPID = "----- pid (\\d{1,}) at (.+) -----$";
    String patternProcessName = "Cmd line: (.+)";
    String patternThreadNum = "DALVIK THREADS \\((\\d+)\\):";
    String patternSystemServiceThread = "^\"(.+)\" sysTid=(\\d+)$";
    String patternThreadStack = "^\"(.+)\" ([\\w,\\s]*)prio=(\\d+) tid=(\\d+) (\\w+)";
    String patternProcessEndLabel = "----- end (\\d+) -----";
    String patternBlank = "^\\s*$";
    public int MAINTHREADID = 1;

    //Thread name maybe duplicated
    private void addThreadStackByName(ThreadStack ts){
        ArrayList<ThreadStack> tsList = threadMapByName.get(ts.name);
        if (tsList == null) {
            tsList = new ArrayList<>();
            tsList.add(ts);
            threadMapByName.put(ts.name, tsList);
        } else {
            tsList.add(ts);
        }
    }

    ThreadStack tStack = null;  //Temporay use to add each stack


    //Handle trace log
    public void addLine(String line) {
        Matcher match = Pattern.compile(patternPID).matcher(line);

        if (match.find()) {
            pid = Integer.parseInt(match.group(1));
            String timeStr = match.group(2)+".000";
            DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.ENGLISH);
            try {
                snapshotTime = format.parse(timeStr);
            } catch  (ParseException e) {
                e.printStackTrace();
            }
            return;
        }

        match = Pattern.compile(patternProcessName).matcher(line);
        if (match.find()) {
            name = match.group(1);
            return;
        }

        match = Pattern.compile(patternThreadNum).matcher(line);
        if (match.find()) {
            return;
        }

        match = Pattern.compile(patternThreadStack).matcher(line);
        try {
            if (match.find()) {
                tStack = new ThreadStack(this);
                tStack.setName(match.group(1))
                        .setPriority(Integer.parseInt(match.group(3)))
                        .setTID(Integer.parseInt(match.group(4)))
                        .setState(match.group(5));
                tStack.process(line);
                threadStackMapByTID.put(tStack.tid, tStack);
                addThreadStackByName(tStack);
                return;
            }
        }catch (Exception e) {
            e.printStackTrace();
        }

        match = Pattern.compile(patternSystemServiceThread).matcher(line);
        if (match.find()) {
            tStack = new ThreadStack(this);
            tStack.setName(match.group(1))
                    .setTID(Integer.parseInt(match.group(2)));
            tStack.process(line);
            threadStackMapByTID.put(tStack.tid, tStack);
            addThreadStackByName(tStack);
            return;
        }



        match = Pattern.compile(patternBlank).matcher(line);
        if (match.find()) {
            tStack = null;
            return;
        }


        match = Pattern.compile(patternProcessEndLabel).matcher(line);
        if (match.find()) {
            //Sometimes the stack is incomplete. In the case, we only have end label
            if (pid == -1) {
                pid = Integer.parseInt(match.group(1));
            }
            return;
        }


        if (tStack != null) {
            tStack.process(line);
        }

    }
    public void setMainThreadID(int id) {
        mainThreadID = id;
    }

    public ThreadStack getThreadByID(int tid){
        return threadStackMapByTID.get(tid);
    }

    public ArrayList<ThreadStack> getThreadByName(String name) {
        ArrayList<ThreadStack> stackList = threadMapByName.get(name);
        return (stackList==null)?(new ArrayList<>()):stackList;
    }

    public Date getSnapShotTime() {
        return snapshotTime;
    }

    //Todo
    public boolean hasDeadLock() {
        return hasDeadlock;
    }

    //Todo
    public boolean isMainThreadBlocked() {
        ThreadStack ts = getMainThread();
        return ts != null && !ts.isUnBlocked;
    }

    public ArrayList<Triage.AnalysisComment> getDeadLockDetail() {
        return deadLockDetail;
    }

    public String getNoStandbyThreadStack() {

        String result = "";

        for (ThreadStack ts: threadStackMapByTID.values()) {
            if (ts.isMainThread() || ts.isBinderThread()) {
                if (!ts.isUnBlocked) {
                    boolean isMatchSample = false;

                    ArrayList<ThreadStack> stackList = Parser.getSampleThreadStack(name, ts.name);
                    for (ThreadStack sampleTs:stackList) {
                        if (sampleTs.equals(ts)) {
                            isMatchSample = true;
                            break;
                        }
                    }

                    if (!isMatchSample) {
                        if (!ts.getBinderFunc().isEmpty()) {
                            System.out.println("Binder function :" + ts.getBinderFunc());
                        }
                        result += ts.getThreadStack();

                    }

                }
            }
        }
        return result;

    }



    public ThreadStack getMainThread() {
        return getThreadByID(mainThreadID);
    }


    public ArrayList<Triage.AnalysisComment> getStackInfo(ThreadStack ts) {
        ArrayList<Triage.AnalysisComment> triageComments = new ArrayList<>();
        Triage.AnalysisComment comment = new Triage.AnalysisComment();
        comment.result = "Thread *"+ts.name+"* (Tid = "+ts.tid+")'s stack is below:";
        comment.referenceLog = ts.logData;
        triageComments.add(comment);
        int lockedThreadID = ts.getLockedByThreadId();
        comment = new Triage.AnalysisComment();
        while (lockedThreadID != ThreadStack.INVALIDTHREADID){
            ThreadStack lts = getThreadByID(lockedThreadID);
            comment.result = "Thread *"+ts.name+"* (Tid = "+ts.tid+")is locked by thread *"+lts.name +" (Tid:"+ ts.tid+")\n";
            comment.result += "Thread *"+lts.name+"* (Tid = "+lts.tid+")'s stack is below:";
            comment.referenceLog =  ts.logData;
            triageComments.add(comment);
            ts = lts;
        }
        if (ts.isUnBlocked) {
            comment.result = "Thread *"+ts.name+"* is not blocked in the trace file.";
            triageComments.add(comment);
        }

        return triageComments;
    }


    public String getBlockedFunc(ThreadStack ts) {
        int lockedThreadID = ts.getLockedByThreadId();
        while (lockedThreadID != ThreadStack.INVALIDTHREADID) {
            ts = getThreadByID(lockedThreadID);
            lockedThreadID = ts.getLockedByThreadId();
        }
        return ts.binderFunc;
    }



    public boolean checkDeadLock() {
        hasDeadlock = false;
        for (ThreadStack ts: threadStackMapByTID.values()) {
            int heldLockThreadID = ts.getLockedByThreadId();
            if (heldLockThreadID != ThreadStack.INVALIDTHREADID) {
                DeadlockDetect dlDetect = new DeadlockDetect(heldLockThreadID);
                if (dlDetect.IsThreadDeadLock()) {
                    hasDeadlock = true;
                    Triage.AnalysisComment comment = new Triage.AnalysisComment();
                    comment.result = "{color:red}Dead Lock{color} has been found!";
                    deadLockDetail.add(comment);
                    ArrayList<Integer> threadsList = dlDetect.GetDeadLockThreads();
                    for (Integer tid:threadsList) {
                        comment = new Triage.AnalysisComment();
                        ThreadStack tsBlocked = threadStackMapByTID.get(tid);
                        ThreadStack tsHold = threadStackMapByTID.get(tsBlocked.getLockedByThreadId());
                        comment.result = "Threads "+tsBlocked.name +" (TID = "+ tid + ") waiting for lock held by "+ tsHold.name + " (TID = "+tsHold.tid+")";
                        comment.referenceLog = tsBlocked.getThreadStack() ;
                        deadLockDetail.add(comment);
                    }
                    break;
                }
            }
        }
        return hasDeadlock;
    }

    class DeadlockDetect {
        private HashSet<Integer> lockSets = new HashSet<>();
        private int threadID = ThreadStack.INVALIDTHREADID;

        public DeadlockDetect(int tid) {
            threadID = tid;
        }

        public boolean IsThreadDeadLock() {

            int tid = getThreadByID(threadID).getLockedByThreadId();

            while (tid != ThreadStack.INVALIDTHREADID) {
                if (lockSets.contains(tid)) {
                    return true;
                } else {
                    lockSets.add(tid);
                    tid = getThreadByID(tid).getLockedByThreadId();
                }
            }
            return false;
        }

        public ArrayList<Integer> GetDeadLockThreads () {
            ArrayList<Integer> threadLists;
            threadLists = new ArrayList<>(lockSets);

            return threadLists;
        }
    }

    public ArrayList<ThreadStack> getBlockedBinderThread() {
        ArrayList<ThreadStack> tsList = new ArrayList<>();
        for (Integer tid: threadStackMapByTID.keySet()) {
            ThreadStack ts = threadStackMapByTID.get(tid);
            if (ts.isBinderThread && !ts.isUnBlocked) {
                tsList.add(ts);
            }
        }
        return tsList;
    }

}
