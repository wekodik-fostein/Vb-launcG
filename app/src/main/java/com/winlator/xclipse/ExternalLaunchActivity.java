package com.winlator.xclipse;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.winlator.XServerDisplayActivity;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.xenvironment.RootFS;

import java.io.File;

/**
 * Entry point for the VB Launcher. Validates the request and forwards it to the EXISTING
 * XServerDisplayActivity with its existing extras (container_id, exec_path). Changes no container, driver or setting.
 *
 * Extras: exec_path (real unix path of a .exe), container_id (optional, default: first container).
 * Result: RESULT_OK if the game activity was started, otherwise RESULT_CANCELED with "error" extra.
 */
public class ExternalLaunchActivity extends Activity {
    public static final String ALLOWED_CALLER = "com.vb.launcher";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String error = launch();
        if (error != null) {
            XclipseLog.i(this, "ExternalLaunch refused: " + error);
            Toast.makeText(this, error, Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED, new Intent().putExtra("error", error));
        }
        else setResult(RESULT_OK);
        finish();
    }

    private String launch() {
        if (!ALLOWED_CALLER.equals(getCallingPackage())) return "caller not allowed (use startActivityForResult from VB Launcher)";

        Intent in = getIntent();
        String execPath = in.getStringExtra("exec_path");
        if (execPath == null || !execPath.toLowerCase().endsWith(".exe")) return "exec_path must be a .exe";
        File file = new File(execPath);
        if (!file.isFile() || !file.canRead()) return "file not readable: " + execPath;

        if (!RootFS.find(this).isValid()) return "Winlator is not set up yet: open it once to install the system files";

        ContainerManager manager = new ContainerManager(this);
        if (manager.getContainers().isEmpty()) return "no container: create one in Winlator first";
        int id = in.getIntExtra("container_id", 0);
        Container container = id > 0 ? manager.getContainerById(id) : manager.getContainers().get(0);
        if (container == null) return "container " + id + " not found";

        Intent game = new Intent(this, XServerDisplayActivity.class);
        game.putExtra("container_id", container.id);
        game.putExtra("exec_path", execPath);
        startActivity(game);
        return null;
    }
}
