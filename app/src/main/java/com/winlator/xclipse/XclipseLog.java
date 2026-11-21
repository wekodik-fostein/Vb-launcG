package com.winlator.xclipse;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Diagnostic logs of the Xclipse layer: files/xclipse/xclipse.log (rotated at 256 KB) and saved reports. */
public final class XclipseLog {
    public static final String TAG = "XclipseLayer";
    private static final long MAX_LOG_BYTES = 256 * 1024;

    private XclipseLog() {}

    private static File dir(Context context) {
        File dir = new File(context.getFilesDir(), "xclipse");
        if (!dir.isDirectory()) dir.mkdirs();
        return dir;
    }

    public static synchronized void i(Context context, String message) {
        Log.i(TAG, message);
        try {
            File log = new File(dir(context), "xclipse.log");
            if (log.length() > MAX_LOG_BYTES) {
                File old = new File(dir(context), "xclipse.log.1");
                old.delete();
                log.renameTo(old);
            }
            String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + " " + message + "\n";
            try (FileOutputStream out = new FileOutputStream(log, true)) {
                out.write(line.getBytes(StandardCharsets.UTF_8));
            }
        }
        catch (IOException ignored) {}
    }

    /** Saves a report to files/xclipse/reports/ and returns the file (null if it could not be written). */
    public static synchronized File saveReport(Context context, String text) {
        try {
            File reports = new File(dir(context), "reports");
            if (!reports.isDirectory()) reports.mkdirs();
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            File file = new File(reports, "report-" + stamp + ".txt");
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(text.getBytes(StandardCharsets.UTF_8));
            }
            return file;
        }
        catch (IOException e) {
            return null;
        }
    }
}
