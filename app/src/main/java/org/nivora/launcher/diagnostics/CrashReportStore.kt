package org.nivora.launcher.diagnostics

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal object CrashReportStore {
    private const val DIRECTORY_NAME = "crash-reports"
    private const val MAX_REPORTS = 5

    fun save(context: Context, thread: Thread, throwable: Throwable) {
        val directory = File(context.filesDir, DIRECTORY_NAME)
        if (!directory.exists() && !directory.mkdirs()) {
            error("Unable to create crash-report directory")
        }

        val timestamp = SimpleDateFormat(
            "yyyy-MM-dd'T'HH-mm-ss.SSS'Z'",
            Locale.US
        ).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        val version = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
                ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }

        val report = CrashReportFormatter.format(
            timestamp = timestamp,
            appVersion = version,
            androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            threadName = thread.name,
            stackTrace = CrashReportFormatter.captureStackTrace(throwable)
        )

        val file = File.createTempFile("crash-", ".txt", directory)
        FileOutputStream(file).use { output ->
            output.write(report.toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }

        directory.listFiles { candidate ->
            candidate.isFile && candidate.name.endsWith(".txt")
        }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(MAX_REPORTS)
            ?.forEach { it.delete() }
    }
}
