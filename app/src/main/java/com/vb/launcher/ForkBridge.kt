package com.vb.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract

/** Direct launch of a game file through the Winlator fork (package com.winlator, ExternalLaunchActivity). */
object ForkBridge {
    const val PKG = "com.winlator"
    private const val ACTIVITY = "com.winlator.xclipse.ExternalLaunchActivity"

    fun isInstalled(ctx: Context): Boolean = runCatching {
        ctx.packageManager.getPackageInfo(PKG, 0)
        // our bridge exists only in the fork, not in stock Winlator
        ctx.packageManager.getActivityInfo(ComponentName(PKG, ACTIVITY), 0)
        true
    }.getOrDefault(false)

    /** Intent for startActivityForResult (the fork checks the calling package). */
    fun launchIntent(exePath: String, containerId: Int = 0) = Intent().apply {
        component = ComponentName(PKG, ACTIVITY)
        putExtra("exec_path", exePath)
        if (containerId > 0) putExtra("container_id", containerId)
    }

    /** SAF document uri -> real file path, or null if it cannot be mapped (Winlator needs real paths). */
    fun uriToPath(uri: Uri): String? {
        if (uri.authority != "com.android.externalstorage.documents") return null
        val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        val vol = id.substringBefore(':')
        val rel = id.substringAfter(':', "")
        if (rel.isEmpty()) return null
        return if (vol == "primary") "/storage/emulated/0/$rel" else "/storage/$vol/$rel"
    }
}
