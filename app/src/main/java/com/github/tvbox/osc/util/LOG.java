package com.github.tvbox.osc.util;

import android.content.Context;
import android.util.Log;

import com.github.tvbox.osc.base.App;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author pj567
 * @date :2020/12/18
 * @description:
 */
public class LOG {
    private static String TAG = "TVBox";
    private static boolean isSaveLog = false;
    private static int saveDay = 2;
    private static File file;

    public static void i(Object... msgs){
        LogPrint(Log.INFO, FormatMsg(msgs));
    }
    public static void d(Object... msgs){
        LogPrint(Log.DEBUG, FormatMsg(msgs));
    }
    public static void w(Object... msgs){
        LogPrint(Log.WARN, FormatMsg(msgs));
    }
    public static void e(Object... msgs){
        LogPrint(Log.ERROR, FormatMsg(msgs));
    }
    public static void OpenSaveLog(){
        LOG.i("LOG", "打开日志存储系统");
        try {
            Context context = App.getInstance().getBaseContext();
            isSaveLog = true;
            if (file == null) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
                Date now = dateFormat.parse(dateFormat.format(new Date(System.currentTimeMillis())));
                File logDir = new File(context.getFilesDir().getAbsolutePath() + "/logs/");
                if (!logDir.exists())
                    logDir.mkdirs();
                else{ // 删除超时日志文件
                    File[] tempList = logDir.listFiles();
                    for (int i = 0; i < tempList.length; i++) {
                        if (tempList[i].isFile()) {
                            String fileName = tempList[i].getName();
                            if (fileName.endsWith(".log")){    //  根据自己的需要进行类型筛选
                                try {
                                    //文件时间减去当前时间
                                    Date start = dateFormat.parse(dateFormat.format(new Date(tempList[i].lastModified())));
                                    long diff = now.getTime() - start.getTime();//这样得到的差值是微秒级别
                                    long days = diff / (1000 * 60 * 60 * 24);
                                    if(saveDay <= days){
                                        tempList[i].delete();
                                    }
                                } catch (Exception e){
                                    e( "OpenSaveLog", "dataformat exeption e " + e.toString());
                                }
                            }
                        }
                    }
                }
                file = new File(logDir, dateFormat.format(now)+".log");
                if (!file.exists())
                    file.createNewFile();
                e("日志文件存储位置：", file.getAbsolutePath());
            }
        }catch (Exception e){
            e.printStackTrace();
            isSaveLog = false;
            file = null;
        }
    }
    public static void ClsoeSaveLog(){
        LOG.i("LOG", "关闭日志存储系统");
        isSaveLog = false;
        if (file!=null)
            file = null;
    }
    private static String FormatMsg(Object... msgs){
        String msgStr = "";
        for (Object msg : msgs){
            msgStr += String.format("%s    ", msg);
        }
        return msgStr;
    }
    private static void LogPrint(int logType, String msg){
        if (logType == Log.ERROR)
            Log.e(TAG, msg);
        else if (logType == Log.WARN)
            Log.w(TAG, msg);
        else if (logType == Log.DEBUG)
            Log.e(TAG, msg);
        else if (logType == Log.INFO)
            Log.i(TAG, msg);
        else
            Log.e(TAG, msg);
        WriteFile(logType, msg);
    }
    private static void WriteFile(int logType, String msg){
        if (isSaveLog && file!=null)
            FileUtils.appendFile(file, String.format("%s   %s\n", logType, msg));
    }
}
