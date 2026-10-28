package org.nivora.launcher.diagnostics

internal object CrashReportFormatter {
    const val MAX_STACK_TRACE_CHARS = 48 * 1024

    fun format(
        timestamp: String,
        appVersion: String,
        androidVersion: String,
        deviceModel: String,
        threadName: String,
        stackTrace: String
    ): String {
        val safeTrace = if (stackTrace.length > MAX_STACK_TRACE_CHARS) {
            stackTrace.take(MAX_STACK_TRACE_CHARS) + "\n[stack trace truncated]"
        } else {
            stackTrace
        }

        return buildString {
            appendLine("Nivora Launcher crash report")
            appendLine("Timestamp: $timestamp")
            appendLine("App version: $appVersion")
            appendLine("Android version: $androidVersion")
            appendLine("Device model: $deviceModel")
            appendLine("Thread: $threadName")
            appendLine()
            appendLine("Stack trace:")
            append(safeTrace)
            if (!safeTrace.endsWith("\n")) append('\n')
        }
    }
}
