package com.wakatime.eclipse.plugin;

import org.eclipse.core.runtime.Status;

public class Logger {

    public static void info(String msg) {
        logMessage(msg, Status.INFO, null);
    }

    public static void debug(String msg) {
        if (WakaTime.DEBUG)
            logMessage(msg, Status.INFO, null);
    }

    public static void debug(String msg, Exception e) {
        if (WakaTime.DEBUG)
            logMessage(msg, Status.ERROR, e);
    }

    public static void debug(Exception e) {
        if (WakaTime.DEBUG)
            logMessage("Debug", Status.ERROR, e);
    }

    public static void error(String msg) {
        logMessage(msg, Status.ERROR, null);
    }

    public static void error(String msg, Exception e) {
        logMessage(msg, Status.ERROR, e);
    }

    public static void error(Exception e) {
        logMessage("Error", Status.ERROR, e);
    }

    public static void warn(String msg, Exception e) {
        logMessage(msg, Status.WARNING, e);
    }

    public static void warn(Exception e) {
        logMessage("Warning", Status.WARNING, e);
    }

    public static void logMessage(String msg, int level, Exception e) {
        if (WakaTime.logInstance != null)
            WakaTime.logInstance.log(new Status(level, WakaTime.PLUGIN_ID, Status.OK, msg, e));
    }
}
