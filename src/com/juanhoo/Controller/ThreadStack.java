package com.juanhoo.Controller;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Yi He on 7/7/2016.
 */
public class ThreadStack {
    public int tid;
    private int threadPriority;
    private String threadState;
    public boolean isUnBlocked = false;
    public String name;
    public boolean isMainThread = false;
    public boolean isBinderThread = false;
    String logData = "";
    public static final int INVALIDTHREADID = -1;
    private int lockedByThreadId = INVALIDTHREADID;
    String binderFunc = "";
    Process parent = null;
    private ArrayList<String> stackFuncList = new ArrayList<>();
    ArrayList<ArrayList<String>> keyWordsArrays = new ArrayList<>();


    private void initKeywordArray() {
        ArrayList<String> keywords = new ArrayList<>();
        keywords.add("talkWithDriver");
        keywords.add("getAndExecuteCommand");
        keywords.add("joinThreadPool");

        keyWordsArrays.add(keywords);

        keywords = new ArrayList<>();
        keywords.add("__epoll_pwait");
        keywords.add("nativePollOnce");
        keyWordsArrays.add(keywords);

        keywords = new ArrayList<>();
        keywords.add("art::ConditionVariable::Wait");
        keywords.add("CallBooleanMethodV");
        keywords.add("android::IPCThreadState::getAndExecuteCommand");
        keywords.add("android::Thread::_threadLoop");

        keyWordsArrays.add(keywords);

        keywords = new ArrayList<>();
        keywords.add("__switch_to");
        keywords.add("art::ConditionVariable::Wait");
        keywords.add("android::Looper::pollInner");
        keywords.add("ava_android_os_MessageQueue_nativePollOnce__JI");

        keyWordsArrays.add(keywords);

        keywords = new ArrayList<>();
        keywords.add("_pselect");
        keywords.add("SocketListener::runListener");
        keywords.add("SocketListener::threadStart");

        keyWordsArrays.add(keywords);

        keywords = new ArrayList<>();
        keywords.add("syscall");
        keywords.add("ConditionVariable::Wait");
        keywords.add("MessageQueue.nativePollOnce");
        keywords.add("android.os.Looper.loop");

        keyWordsArrays.add(keywords);

        keywords = new ArrayList<>();
        keywords.add("__epoll_pwait");
        keywords.add("ConditionVariable::Wait");

        keyWordsArrays.add(keywords);
    }

    public ThreadStack(Process pr) {
        parent = pr;
        initKeywordArray();
    }

    private boolean isUnBlockedThreadStack(String line) {
        for (ArrayList<String>keywords:keyWordsArrays) {
            for (String keyword:keywords) {
                if (line.contains(keyword)) {
                    keywords.remove(keyword);
                    if (keywords.size() == 0) {
                        return true;
                    } else {
                        break;
                    }
                }
            }
        }
        return false;
    }



    private static String getFunctionName(String line){
        String patternAT = "^\\s+at (.*)\\((.*)\\)";
        String patternNative = "^\\s+native: #[\\d]+ pc (.*)  (.*) \\((.*)\\+(\\d+)\\)";
        String patterkernel =  "^\\s+kernel: (.*)\\+([\\dx]+)\\/([\\dx]+)";

        //  System.out.println(line);
        if (line.contains("at ")) {
            Matcher matcher = Pattern.compile(patternAT).matcher(line);
            if (matcher.find()) {
                //   System.out.println(matcher.group(1));
                //  String patternsub = "(.*):(\\d+)";
                //   Matcher subMatcher = Pattern.compile(patternsub).matcher(matcher.group(2));
                String func = matcher.group(1);
                if (func.lastIndexOf('.') != -1) {
                    func = func.substring(func.lastIndexOf('.') + 1, func.length());
                }
                return func;
            }

        } else if (line.contains("native: ")) {

            Matcher matcher = Pattern.compile(patternNative).matcher(line);
            if (matcher.find()) {
                //    System.out.println(matcher.group(3));
                return (matcher.group(3));

            }

        } else if (line.contains("kernel: ")) {

            Matcher matcher = Pattern.compile(patterkernel).matcher(line);
            if (matcher.find()) {
                //     System.out.println(matcher.group(1));
                return (matcher.group(1));
            }

        }
        return "";
    }



    @Override
    public boolean equals(Object obj) {

        if (getClass() != obj.getClass()) {
            return false;
        }
        ThreadStack compareStack = (ThreadStack) obj;
        if (compareStack.name.compareToIgnoreCase(name) != 0){
            return false;
        }

        if (stackFuncList.size() != compareStack.stackFuncList.size()) {
            return false;
        }

        for (int i = 0; i < stackFuncList.size(); i++){
            if (stackFuncList.get(i).compareToIgnoreCase(compareStack.stackFuncList.get(i)) != 0) {
                return false;
            }
        }

        return true;
    }

    public ThreadStack setName(String name) {
        this.name = name;
        if (name.contains("main") ||
                (parent.name != null && parent.name.contains(name))) {
            isMainThread = true;
            return  this;
        }

        if (name.contains("Binder_")) {
            isBinderThread = true;
        }
        return this;
    }

    public boolean isMainThread() {
        return isMainThread;
    }

    public boolean isBinderThread() {
        return isBinderThread;
    }

    public ThreadStack setPriority(int prio) {
        threadPriority = prio;
        return this;
    }

    public ThreadStack setTID(int tid) {
        this.tid = tid;
        if (isMainThread) {
            parent.setMainThreadID(tid);
        }
        return this;
    }

    public ThreadStack setState(String state) {
        threadState = state;
        return this;
    }


    String preProcessedLine = "";


    public void process(String line) {
        logData += line + "\n";
        if (lockedByThreadId == INVALIDTHREADID) {
            checkWaitingForSync(line);
        }
        if (!isUnBlocked && isUnBlockedThreadStack(line))  {
            isUnBlocked = true;
        }

        String funcName = getFunctionName(line);
        if (funcName.length() != 0) {
            stackFuncList.add(funcName);
        }

        if (isBinderThread) {
            if (binderFunc.length() == 0 && line.contains("onTransact(")) {
                binderFunc = getFunctionName(preProcessedLine);
            }
        } else {
            if (binderFunc.length() == 0 && preProcessedLine.contains("android.os.BinderProxy.transact(")){
                binderFunc = getFunctionName(line);
            }
        }

        preProcessedLine = line;
        if (binderFunc != null) {
            Parser.addRunningBinder(binderFunc, this);
        }
    }

    public String getBinderFunc(){
        return binderFunc;
    }

    private void checkWaitingForSync(String line){
        String patternLock = "^\\s*- waiting to lock <(.*)> \\((.*)\\) held by thread (\\d+)$";

        Matcher match = Pattern.compile(patternLock).matcher(line);
        if (match.find()) {
            lockedByThreadId = Integer.parseInt(match.group(3));
        }
    }


    public String getThreadStack() {
        return logData;
    }

    @Override
    public String toString() {
        return logData;
    }

    public int getLockedByThreadId() {
        return lockedByThreadId;
    }

    private String timeConsumedKeyWords [] = {"download", "decode"};

    public boolean isTimeCounsmedLoad() {
        for (String funcName:stackFuncList) {
            for (String keyword:timeConsumedKeyWords) {
                if (funcName.toLowerCase().contains(keyword)) {
                    return true;
                }
            }
        }
        return false;
    }



}
