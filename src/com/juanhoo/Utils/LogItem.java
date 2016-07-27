package com.juanhoo.Utils;

import java.util.Comparator;

/**
 * Created by Yi He on 7/15/2016.
 */
public class LogItem implements Comparator<LogItem>,Comparable<LogItem>{
    public long time;
    public String data;

    LogItem(long t, String line) {
        time = t;
        data = line;
    }

    @Override
    public int compare(LogItem o1, LogItem o2) {
        if (o1.time > o2.time) {
            return 1;
        }
        if (o1.time < o2.time) {
            return -1;
        }
        return 0;
    }

    @Override
    public int compareTo(LogItem o) {
        if (o.time < time) {
            return 1;
        }
        if (o.time > time) {
            return -1;
        }
        return 0;
    }
}