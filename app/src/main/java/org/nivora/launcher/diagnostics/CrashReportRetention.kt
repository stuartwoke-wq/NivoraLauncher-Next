package org.nivora.launcher.diagnostics

import java.io.File

/**
 * Best-effort retention for locally stored crash reports.
 *
 * Only regular .txt report files are eligible for cleanup.
 * Filesystem deletion can fail, so the limit is a target rather
 * than a guarantee when storage operations fail.
 */
internal object CrashReportRetention {
    const val MAX_REPORTS = 5

    fun prune(directory: File) {
        val reports = directory.listFiles { file ->
            file.isFile && file.name.endsWith(".txt")
        } ?: return

        reports
            .sortedWith(
                compareByDescending<File> { it.lastModified() }
                    .thenByDescending { it.name }
            )
            .drop(MAX_REPORTS)
            .forEach { report ->
                try {
                    report.delete()
                } catch (_: SecurityException) {
                    // Cleanup must not prevent crash reporting from completing.
                }
            }
    }
}
