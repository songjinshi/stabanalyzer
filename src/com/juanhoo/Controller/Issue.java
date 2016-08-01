package com.juanhoo.Controller;

import com.juanhoo.Utils.Triage;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by Yi He on 7/28/2016.
 * This is a sample library project
 */
public class Issue implements Comparator<Issue>,Comparable<Issue>{
    public int pid = -1;
    public int tid = -1;
    public int uid = -1;
    public String pName;
    public String tName;
    public Date time;
    public String reason = "";
    public String logData = "";
    public IssueType issueType;
    public Date sameIssueTime;
    public String buildFingerPrint= "";
    public String keyWord = Triage.DEFAULT;
    public String dependenPName = null;

    @Override
    public int compare(Issue o1, Issue o2) {
        if (o1.time.getTime() > o2.time.getTime()) {
            return 1;
        }
        if (o1.time.getTime() < o2.time.getTime()) {
            return -1;
        }
        return 0;
    }

    public enum IssueType {
        ANR, FORCECLOSE, TOMBSTONE, WATCHDOG, MODEMPANIC;

        @Override
        public String toString() {
            return super.toString();
        }
    }

    public Issue() {

    }

    public Issue(IssueType type, String line) {

    }

    @Override
    public int compareTo(Issue o) {
        if (time == null || o.time == null) {
            return -1;
        }
        if (o.time.getTime() < time.getTime()) {
            return -1;
        }
        if (o.time.getTime() > time.getTime()) {
            return 1;
        }
        return 0;
    }

    public boolean isSameRootCauseIssue(Issue e) {
        if (reason == null || e.reason == null) {
            return false;
        }
        if (pName.compareTo(e.pName) != 0) {
            return false;
        }
        if (reason.compareTo(e.reason) != 0) {
            return false;
        }
        return true;
    }

    public boolean isSameIssue(Issue e) {
        if (pid == e.pid && tid == e.tid) {
            return true;
        }
        return false;
    }

}
