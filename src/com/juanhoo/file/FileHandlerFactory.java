package com.juanhoo.file;

import com.juanhoo.Controller.Parser;

/**
 * Created by JH on 2016/7/6.
 */
public class FileHandlerFactory {

    public static FileHandler getLogFileHandle(Parser p, int type) {

        switch (type) {
            case Parser.EVENTLOG:
                return new EventFileHandler(p);
            case Parser.SYSTEMLOG:
                return new SystemFileHandler(p);
            case Parser.TRACELOG:
                return new TraceFileHandler(p);
            case Parser.BUGREPORTLOG:
                return new BugreportFileHandler(p);
            case Parser.CRASHLOG:
                return new CrashFileHandler(p);
            case Parser.ENTRYLOG:
                return new EntryFileHandler(p);
            case Parser.PROCESSLIST:
                return new ProcesslistFileHandler(p);
            case Parser.REPORTINFOLOG:
                return new ReportInfoHandler(p);
            case Parser.MAINLOG:
                return new MainFileHandler(p);

            default:
                return null;
        }
    }
}
