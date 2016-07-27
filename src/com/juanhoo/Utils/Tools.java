package com.juanhoo.Utils;

import java.io.File;

/**
 * Created by Yi He on 7/9/2016.
 */
public class Tools {
    static public boolean isNumeric(String s) {
        return s.matches("[-+]?\\d*\\.?\\d+");
    }

    public static String getBugreportFileName(String logFolder, String keyword) {
        File folder = new File(logFolder);
        String bugreportFileName;

        File[] listOfFiles = folder.listFiles();
        if (listOfFiles == null) {
            return null;
        }
        for (File file : listOfFiles) {
            if (file.isFile() && file.getName().toLowerCase().contains(keyword)) {
                bugreportFileName = file.getName();
                return logFolder + File.separator +bugreportFileName;
            }
        }
        return null;
    }

    /*
    MessageDigest md = MessageDigest.getInstance("MD5");
try (InputStream is = Files.newInputStream(Paths.get("file.txt"));
     DigestInputStream dis = new DigestInputStream(is, md))
{
  Read decorated stream (dis) to EOF as normal...
}
byte[] digest = md.digest();
     */


}
