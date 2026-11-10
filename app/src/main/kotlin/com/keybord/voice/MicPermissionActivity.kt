package com.keybord.voice

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Lightweight translucent Activity to request RECORD_AUDIO permission when triggered
 * from the Keybord InputMethodService (which cannot invoke runtime permission dialogs directly).
 */
class MicPermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            notifyResult(granted = true)
            finish()
            return
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQ_MIC_PERMISSION
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = requestCode == REQ_MIC_PERMISSION &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        notifyResult(granted)
        finish()
    }

    private fun notifyResult(granted: Boolean) {
        sendBroadcast(
            Intent(ACTION_MIC_PERMISSION_RESULT).apply {
                setPackage(packageName)
                putExtra(EXTRA_GRANTED, granted)
            }
        )
    }

    companion object {
        const val REQ_MIC_PERMISSION = 7001
        const val ACTION_MIC_PERMISSION_RESULT = "com.keybord.ACTION_MIC_PERMISSION_RESULT"
        const val EXTRA_GRANTED = "granted"
    }
}
