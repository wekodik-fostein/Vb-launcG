package com.winlator.xclipse;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.winlator.R;
import com.winlator.contentdialog.ContentDialog;

/** On-demand diagnostics dialog. Collects the report on a background thread; nothing runs at app start. */
public class XclipseReportDialog extends ContentDialog {
    private volatile String reportText = "";

    public XclipseReportDialog(Context context) {
        super(context, R.layout.xclipse_report_dialog);
        setTitle(R.string.xclipse_report_title);
        setIcon(R.drawable.icon_display_settings);
        findViewById(R.id.BTCancel).setVisibility(View.GONE);

        final TextView tvReport = findViewById(R.id.TVXclipseReport);
        final Button btShare = findViewById(R.id.BTXclipseShare);
        btShare.setEnabled(false);
        tvReport.setText(R.string.xclipse_report_collecting);

        final Context appContext = context.getApplicationContext();
        final Handler handler = new Handler(Looper.getMainLooper());

        btShare.setOnClickListener((v) -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, "Xclipse diagnostics");
            send.putExtra(Intent.EXTRA_TEXT, reportText);
            Intent chooser = Intent.createChooser(send, null);
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            appContext.startActivity(chooser);
        });

        new Thread(() -> {
            String text;
            try {
                text = DiagnosticReport.build(appContext);
                XclipseLog.i(appContext, "report generated (" + text.length() + " chars)");
                XclipseLog.saveReport(appContext, text);
            }
            catch (Throwable t) {
                text = "Report failed: " + t;
                XclipseLog.i(appContext, text);
            }
            final String result = text;
            handler.post(() -> {
                reportText = result;
                tvReport.setText(result);
                btShare.setEnabled(true);
            });
        }, "XclipseReport").start();
    }
}
