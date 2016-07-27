package com.juanhoo.Utils;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.PriorityQueue;

/**
 * Created by Yi He on 7/15/2016.
 */
public class MergeQueue {
    static int dbgRec = 0;
    PriorityQueue<LogItem> queue = new PriorityQueue<>();

    public void add(long t, String line) {
        LogItem item = new LogItem(t, line);
        queue.add(item);
        //Limit 2000
        if (queue.size() > 2000) {
            queue.poll();
        }
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public LogItem poll() {
        return queue.poll();
    }

}
