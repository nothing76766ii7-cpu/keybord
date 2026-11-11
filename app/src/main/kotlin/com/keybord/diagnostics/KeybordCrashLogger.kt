package com.keybord.diagnostics

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Lightweight privacy-safe diagnostics & crash logger for Keybord IME and Settings.
 * - Captures unhandled crashes cleanly before delegating to Android's default handler.
 * - Records non-fatal errors (InputConnection, SQLite, Prediction, AI Writer) with component context.
 * - Strictly excludes typed keystrokes, text fields, passwords, clipboard text, and API keys.
 */
object KeybordCrashLogger {

    private const val TAG = "KeybordDiag"
    private const val LOG_FILE_NAME = "keybord_crash.log"
    private const val MAX_LOG_BYTES = 96 * 1024

    @Volatile
    private var installed = false

    @Volatile
    private var appContext: Context? = null

    private var previousHandler: Thread.UncaughtExceptionHandler? = null
    private val lock = Any()

    fun install(context: Context) {
        appContext = context.applicationContext ?: context
        if (installed) return
        synchronized(lock) {
            if (installed) return
            previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                try {
                    writeEntry(
                        level = "FATAL",
                        component = "Uncaught[${thread.name}]",
                        summary = throwable.javaClass.name + ": " + (throwable.message ?: "no message"),
                        throwable = throwable,
                        meta = emptyMap()
                    )
                } catch (_: Throwable) {
                    // Never block default crash propagation
                }
                previousHandler?.uncaughtException(thread, throwable)
            }
            installed = true
        }
    }

    fun recordNonFatal(
        component: String,
        summary: String,
        throwable: Throwable? = null,
        meta: Map<String, String> = emptyMap()
    ) {
        if (throwable != null) {
            Log.e(TAG, "[$component] $summary", throwable)
        } else {
            Log.w(TAG, "[$component] $summary")
        }
        writeEntry("WARN", component, summary, throwable, meta)
    }

    fun recordInfo(component: String, summary: String, meta: Map<String, String> = emptyMap()) {
        Log.i(TAG, "[$component] $summary")
        writeEntry("INFO", component, summary, null, meta)
    }

    fun readLogs(context: Context): String {
        val ctx = context.applicationContext ?: context
        synchronized(lock) {
            val file = File(ctx.filesDir, LOG_FILE_NAME)
            if (!file.exists()) return ""
            return try {
                file.readText()
            } catch (_: Exception) {
                ""
            }
        }
    }

    fun clearLogs(context: Context) {
        val ctx = context.applicationContext ?: context
        synchronized(lock) {
            val file = File(ctx.filesDir, LOG_FILE_NAME)
            try {
                if (file.exists()) file.writeText("")
            } catch (_: Exception) {
                // Ignore
            }
        }
    }

    private fun writeEntry(
        level: String,
        component: String,
        summary: String,
        throwable: Throwable?,
        meta: Map<String, String>
    ) {
        val ctx = appContext ?: return
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val sb = StringBuilder()
        sb.append("[$ts] $level [$component] (SDK ${Build.VERSION.SDK_INT}) $summary\n")
        if (meta.isNotEmpty()) {
            val safeMeta = meta.entries.joinToString(", ") { "${it.key}=${sanitizeMeta(it.value)}" }
            sb.append("  meta: $safeMeta\n")
        }
        if (throwable != null) {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val traceLines = sw.toString().lineSequence().take(28).joinToString("\n")
            sb.append(traceLines).append('\n')
        }
        sb.append("---\n")

        synchronized(lock) {
            try {
                val file = File(ctx.filesDir, LOG_FILE_NAME)
                val existing = if (file.exists() && file.length() > MAX_LOG_BYTES) {
                    val text = file.readText()
                    val tailStart = (text.length - MAX_LOG_BYTES / 2).coerceAtLeast(0)
                    "[...older logs truncated...]\n" + text.substring(tailStart)
                } else if (file.exists()) {
                    file.readText()
                } else {
                    ""
                }
                file.writeText(existing + sb.toString())
            } catch (_: Exception) {
                // Avoid recursive logging on disk failure
            }
        }
    }

    private fun sanitizeMeta(raw: String): String {
        return raw.replace("\n", " ").replace("\r", " ").take(120)
    }
}
