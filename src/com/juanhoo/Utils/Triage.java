package com.juanhoo.Utils;

import com.juanhoo.Controller.Parser;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by Yi He on 7/10/2016.
 */
public class Triage {
    private String tagLog = "*Log*";
    private String comments = "";
    private String tagAnalysis = "*Initial Analysis Result*";
    private String tagNextStep = "*Next Step*";
    private Parser parser;
    public static String DEFAULT = "default";

    private HashMap<String, HashMap<String,String>> componentTriageMap = new HashMap<>();

    public String getComments() {
        return comments;
    }

    public String getHtmlFormatComments() {
        return convertJiraFormatToHtml(comments);
    }


    private void addTriageRule(String processName, String keyWord, String component) {
        HashMap<String, String> map = componentTriageMap.get(processName);
        if (map == null) {
            map = new HashMap<>();
            map.put(keyWord, component);
            componentTriageMap.put(processName, map);
        } else {
            map.put(keyWord, component);
        }
    }

    private void initTriageMap() {
        addTriageRule("com.android.launcher3",DEFAULT, "Core-AOSP launcher" );
        addTriageRule("/system/bin/mm-qcamera-daemon",DEFAULT, "MME-Camera");
        addTriageRule("com.android.phone", "MobileNetworkSettings", "Core-Settings");
        addTriageRule("com.android.phone", DEFAULT, "Framework HAL-Telephony");
        addTriageRule("system_server", DEFAULT, "Framework HAL-Runtime");
        addTriageRule("com.google.android.gms.persistent", DEFAULT, "Google - GMS Framework");
        addTriageRule("com.facebook.orca", DEFAULT, "3rd Party - MotoDev");
        addTriageRule("com.motorola.cameraone", DEFAULT, "MME - Camera App");


    }

    public Triage(Parser p) {
        parser = p;
        initTriageMap();
    }



    //Return componet team
    public String QueryAssignComponent(String processName, String keyWord) {

        if (parser.isThirdPartyProcess(processName)) {
            return "3rd Party - MotoDev";
        }

        HashMap<String, String> map = componentTriageMap.get(processName);
        if (map != null) {
            return map.get(keyWord);
        }
        return null;
    }

    public void addReferenceLogName(String logName) {
        if (comments.contains(logName)) {
            return;
        }
        comments += tagLog +"\n";
        comments += logName +"\n";
    }

    public static String highlight(String comment) {
        return "*"+comment+"*";
    }

    public void addSimpleComment(String comment) {
        comments += comment + "\n";
    }



    public void addAnalysisResult(ArrayList<AnalysisComment> analysisComments) {
        if (!comments.contains(tagAnalysis)) {
            comments += tagAnalysis + "\n";
        }
        for (AnalysisComment analysisComment:analysisComments) {
            if (analysisComment.result.length() != 0) {
                comments += analysisComment.result + "\n";
            }
            if (analysisComment.referenceLog != null && !analysisComment.referenceLog.isEmpty()) {
                //"{noformat}\n" + ts.logData + "{noformat}\n" ;
                comments +=  "{noformat}\n" +analysisComment.referenceLog +   "{noformat}\n";
            }
            if (analysisComment.hideLog.length() != 0) {
                comments += "<p class=\"hidelog\">" + analysisComment.hideLog +  "</p>";
            }
        }
    }

    public void addNextStep(String triageComment) {
        comments += "\n"+tagNextStep + "\n";
        comments += triageComment + "\n";
    }

    public static class AnalysisComment {
        public String result = "";
        public String referenceLog = "";
        public String hideLog = "";


    }

    String outputTemplate =
            "<html>\n" +
                    "    <head>\n" +
                    "    <title>Bug2GO analysis result</title>\n" +
                    "<script src=\"https://ajax.googleapis.com/ajax/libs/jquery/1.12.2/jquery.min.js\"></script>\n" +
                    "<script>\n" +
                    "$(document).ready(function(){\n" +
                    "    $(\"p[class='hidelog']\").hide();\n" +
                    "    var showtitle = 'Show the crash process log';\n" +
                    "    var hidetitle = 'Hide the crash process log';\n" +
                    "    $(\"#btnhideshow\").click(function(){        \n" +
                    "        if ($(\"#btnhideshow\").text() == showtitle) {\n" +
                    "            $(\"p[class='hidelog']\").show();\n" +
                    "            $(\"#btnhideshow\").html(hidetitle);\n" +
                    "        } else {\n" +
                    "            $(\"p[class='hidelog']\").hide();\n" +
                    "            $(\"#btnhideshow\").html(showtitle);\n" +
                    "        }                \n" +
                    "    });\n" +
                    "});\n" +
                    "</script>\n" +
                    "    <style>\n" +
                    "    p.log {\n" +
                    "      border-style: solid;\n" +
                    "      border-width: 1px;\n" +
                    "      background: lightgray\n" +
                    "    }\n" +
/*                    "    p.title {\n" +
                    "      font-weight: bold;;\n" +
                    "    }\n" +*/
                    "    p.hidelog {\n" +
                    "      border-style: solid;\n" +
                    "      border-width: 1px;\n" +
                    "      background: white\n" +
                    "    }\n" +
                    "   .highlight {\n" +
                    "      color: red;\n" +
                    "      font-weight: bold;\n" +
                    "    }\n" +
                    "    .attention {\n" +
                    "      color: blue;\n" +
                    "      font-weight: bold;\n" +
                    "    }"+
                    "    p { margin:0 }  "+
                    "    </style>\n" +
                    "    </head>\n" +
                    "<body>\n" +
                    "<button id='btnhideshow'>Show the crash process log</button>\n"+
                    "<hr>\n" +
                    "DummyComments\n" +
                    "</body>\n" +
                    "</html>";

    public void generateOutput(String outputFileName) {
        String formatComments;
        formatComments = convertJiraFormatToHtml(comments);
        formatComments = outputTemplate.replace("DummyComments", formatComments);
        formatComments = formatComments.replaceAll("\\{color:red\\}", "\\{color:red\\}<span class=\"highlight\">" )
                .replaceAll("\\{color\\}", "</span>\\{color\\}");


        //  comments = comments.replaceAll("\\n", "<br>");
        // String lines[] = comments.split("\\n");
        try {
            PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(outputFileName, false)));
          /*  for (String line:lines) {
                line = line + "</p>";
                writer.println(line);
            }*/
            writer.println(formatComments);

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        String cmd = "cmd /c start chrome "+ outputFileName;
        try {
            Process child = Runtime.getRuntime().exec(cmd);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String convertJiraFormatToHtml(String comments) {
        //comments = comments.replaceAll("\\n", "<br>");
        String commentArray[] = comments.split("\\n");
        comments = "";
        boolean inFormatblock = false;
        boolean inList = false;
        for (int i = 0; i < commentArray.length; i++) {

            if (commentArray[i].contains("noformat")) {
                if (!inFormatblock) {
                    commentArray[i] =  "<p class=\"log\">" + commentArray[i] +"<br>";
                    inFormatblock = true;
                } else {
                    commentArray[i] +=  "</p>";
                    inFormatblock = false;
                }
                comments += commentArray[i];
                continue;
            }
            if (commentArray[i].indexOf("* ") ==  0) {
                if (!inList) {
                    commentArray[i] = "<ul>\n<li>\n" +commentArray[i] +"<br>";
                    inList = true;
                } else {
                    commentArray[i] = "<li>" + commentArray[i] +"<br>";
                }
                comments += commentArray[i] +"\n";
                continue;
            }
            if (commentArray[i].length() == 0) {
                if (inList  && !inFormatblock) {
                    comments += "</ul>\n";
                    inList = false;
                }
                continue;
            }
            if (commentArray[i].charAt(0) == '*'
                    && commentArray[i].charAt(commentArray[i].length() - 1)  == '*'
                    && !inFormatblock) {
                inList = false;
              //  commentArray[i] = "</ul>\n" + commentArray[i];
               // commentArray[i] = "<p class=\"title\">" + commentArray[i] +"</p>";
                commentArray[i] = "</ul><b>" + commentArray[i] +"</b><br>";
            } else {
                if (!inFormatblock && commentArray[i].indexOf('*') >= 0) {
                    boolean pair = false;
                    int ind = commentArray[i].indexOf('*');
                    int pre;
                    String temp = commentArray[i].substring(0, ind);
                    while (ind >= 0) {
                        pre = ind;
                        ind = commentArray[i].indexOf('*', ind+1);
                        if (ind < 0) {
                            temp = temp + commentArray[i].substring(pre+1);
                            break;
                        }
                        if (!pair) {
                            pair = true;
                            temp = temp +"<b>" + commentArray[i].substring(pre, ind+1) +"</b>";
                        } else if (ind > 0){
                            pair = false;
                            temp = temp + commentArray[i].substring(pre, ind+1);
                        }
                    }
                    commentArray[i] = temp;
                }
                commentArray[i] = commentArray[i] + "<br>";
            }

            comments += commentArray[i] +"\n";

        }
        if (inList) {
            comments += "</ul>\n";
        }
        return comments;
    }

}
