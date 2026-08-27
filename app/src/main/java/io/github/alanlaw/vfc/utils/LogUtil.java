package io.github.alanlaw.vfc.utils;

import android.util.Log;

import io.github.alanlaw.vfc.api101.Api101Runtime;

public class LogUtil {
    public static void log(String message) {
        if (message == null) {
            message = "null";
        }
        if (Api101Runtime.getModule() != null) {
            Api101Runtime.getModule().log(Log.INFO, "CamSwap", message);
        } else {
            Log.i("LSPosed-Bridge", message);
        }
    }
}
