package org.nivora.launcher.diagnostics

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal data class CrashReportSummary(
    val fileName: String,
    val lastModified: Long,
    val sizeBytes: Long
)

internal object CrashReportStore {
    private const val DIRECTORY_NAME = "crash-reports"

    /**
     * Lists metadata for the newest retained crash reports.
     * This method does not read report contents or modify files.
     */
    fun listReports(context: Context): List<CrashReportSummary> {
        return listReportsIn(File(context.filesDir, DIRECTORY_NAME))
    }

    /**
     * Lists report metadata from a directory without reading or modifying files.
     * Kept independent of Android Context so its behavior can be unit tested.
     */
    internal fun listReportsIn(directory: File): List<CrashReportSummary> {
        if (!directory.isDirectory) return emptyList()

        val files = directory.listFiles { file ->
            file.isFile && file.name.endsWith(".txt")
        } ?: return emptyList()

        return files
            .sortedWith(
                compareByDescending<File> { it.lastModified() }
                    .thenByDescending { it.name }
            )
            .take(CrashReportRetention.MAX_REPORTS)
            .map { file ->
                CrashReportSummary(
                    fileName = file.name,
                    lastModified = file.lastModified(),
                    sizeBytes = file.length()
                )
            }
    }

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

        CrashReportRetention.prune(directory)
    }
}
