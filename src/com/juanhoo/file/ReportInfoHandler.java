package com.juanhoo.file;

import com.juanhoo.Controller.CrashStack;
import com.juanhoo.Controller.Parser;
import com.juanhoo.Controller.Tombstone;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/*
"totalMem": 1522,
            "VERSION.SDK_INT": "23",
            "uid": "NVBR2C0018",
            "softwareVersion": "addison-userdebug 6.0.1 MPN24.98 2197 intcfg,test-keys",
            "bpVersion": "M8953_1000.30.04.34R ADDISON_ROWDSDS_CUST",
            "PRODUCT": "addison",
            "MODEL": "XT1635-02",
            "BOARD": "msm8953",
            "BRAND": "motorola",
            "SERIAL": "NVBR2C0018",
            "TELEPHONY_DEVICE_ID": "358197070010691",
            "WIFI_MAC": "f4:f1:e1:8a:45:82",
            "TYPE": "userdebug",
            "BUILDID": "MPN24.98",
            "BUILDFINGERPRINT": "motorola\/addison\/addison:6.0.1\/MPN24.98\/2197:userdebug\/intcfg,test-keys",
            "HW_VERSION": "0x8300"
        },
        "userInfo": {
            "email": "wuc051@motorola.com",
            "first_name": "Umberto",
            "last_name": "Corradin",
            "phone": "+5519997647272",
            "coreid": "wuc051"
        },
        "survey": [],
        "category": "A:Tombstone",
        "summary": "SYSTEM_TOMBSTONE",
 */
/**
 * Created by Yi He on 7/9/2016.
 */
public class ReportInfoHandler extends FileHandler{
    Parser.UserInfo userInfo = new Parser.UserInfo();
    Parser.DeviceInfo deviceInfo = new Parser.DeviceInfo();


    public enum InfoInd { EMAIL, COREID, HWVER,BUILDID, TYPE, MODEL,ISSUESUMMARY,SWVER,BUILDFINGERPRINT, BPVERSION};
    HashMap<String,Enum<?>> infoMap;


    void initKeySearchMap () {
        infoMap= new HashMap<>();
        infoMap.put("email", InfoInd.EMAIL);
        infoMap.put("coreid", InfoInd.COREID);
        infoMap.put("HW_VERSION", InfoInd.HWVER);
        infoMap.put("BUILDID", InfoInd.BUILDID);
        infoMap.put("TYPE", InfoInd.TYPE);
        infoMap.put("MODEL", InfoInd.MODEL);
        infoMap.put("summary", InfoInd.ISSUESUMMARY);
        infoMap.put("softwareVersion", InfoInd.SWVER);
        infoMap.put("BUILDFINGERPRINT", InfoInd.BUILDFINGERPRINT);
        infoMap.put("bpVersion", InfoInd.BPVERSION);
    }


    private void storeInfo(InfoInd ind, String data) {
        switch (ind) {
            case EMAIL:
                userInfo.userEmail = data;
                break;
            case COREID:
                userInfo.userCoreID = data;
                break;
            case HWVER:
                deviceInfo.hwVer = data;
                break;
            case BUILDID:
                deviceInfo.buildID = data;
                break;
            case TYPE:
                deviceInfo.swType = data;
                break;
            case MODEL:
                deviceInfo.model = data;
                break;
            case ISSUESUMMARY:
                deviceInfo.issueSummary = data;
                break;
            case SWVER:
                deviceInfo.swVer = data;
                break;
            case BPVERSION:
                deviceInfo.bpVer = data;
                break;
            case BUILDFINGERPRINT:
                data = data.replaceAll("\\\\/", "/");
                parser.setBuildFingerPrint(data);  //The fingerprint looks like not right
                break;
        }
    }

    public ReportInfoHandler(Parser p) {
        super(p);
        initKeySearchMap();
    }




    @Override
    public boolean process() {
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(fileName));
            String line;
            while ((line = br.readLine()) != null) {
                showProgress();
                for (String key:infoMap.keySet()) {
                    String pattern = "\\s+\""+key+"\":\\s+\"(.*?)\".*";
                    Matcher match = Pattern.compile(pattern).matcher(line);
                    if (match.find()) {
                        String data = match.group(1);
                        storeInfo((InfoInd) infoMap.get(key), data);
                    }
                }

            }
        } catch (FileNotFoundException e) {
            System.out.println("Couldn't open file " + fileName);
            e.printStackTrace();
        } catch (IOException e) {
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
        parser.setDeviceInfo(deviceInfo);
        parser.setUserInfo(userInfo);
        return true;
    }
}
