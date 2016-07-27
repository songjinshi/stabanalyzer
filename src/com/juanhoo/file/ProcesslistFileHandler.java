package com.juanhoo.file;

import com.juanhoo.Controller.Parser;
import com.juanhoo.Utils.Tools;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Created by Yi He on 7/9/2016.
 */
public class ProcesslistFileHandler extends FileHandler {

    public ProcesslistFileHandler(Parser p) {
        super(p);
    }

    @Override
    public boolean process() {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(fileName));
            String line;
            while ((line = br.readLine()) != null) {
                showProgress();
                String[] items = line.split("\\s+");
                if (items.length < 8) {
                    continue;
                }
                if (Tools.isNumeric(items[1])) {
                    Integer pid = Integer.parseInt(items[1]);
                    String pName = items[7];
                    parser.addProcessRecord(pid, pName);
                }
            }
        }  catch (IOException e) {
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
