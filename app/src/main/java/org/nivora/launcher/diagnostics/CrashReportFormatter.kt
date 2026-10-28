package org.nivora.launcher.diagnostics

import java.io.PrintWriter
import java.io.Writer

internal object CrashReportFormatter {
    const val MAX_STACK_TRACE_CHARS = 48 * 1024
    private const val TRUNCATION_MARKER = "\n[stack trace truncated]"

    fun captureStackTrace(throwable: Throwable): String {
        val writer = BoundedTraceWriter(MAX_STACK_TRACE_CHARS)
        PrintWriter(writer).use { throwable.printStackTrace(it) }
        return writer.toString()
    }

    fun format(
        timestamp: String,
        appVersion: String,
        androidVersion: String,
        deviceModel: String,
        threadName: String,
        stackTrace: String
    ): String {
        val safeTrace = if (stackTrace.length > MAX_STACK_TRACE_CHARS) {
            stackTrace.take(MAX_STACK_TRACE_CHARS - TRUNCATION_MARKER.length) +
                TRUNCATION_MARKER
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

    private class BoundedTraceWriter(
        private val limit: Int
    ) : Writer() {
        private val buffer = StringBuilder(limit)
        private var truncated = false

        override fun write(chars: CharArray, offset: Int, length: Int) {
            if (offset < 0 || length < 0 || offset > chars.size - length) {
                throw IndexOutOfBoundsException()
            }
            if (truncated || length == 0) return

            val markerLength = TRUNCATION_MARKER.length
            val contentLimit = limit - markerLength
            val remaining = contentLimit - buffer.length
            val accepted = minOf(length, remaining)

            if (accepted > 0) {
                buffer.append(chars, offset, accepted)
            }

            if (accepted < length) {
                buffer.append(TRUNCATION_MARKER)
                truncated = true
            }
        }

        override fun flush() = Unit

        override fun close() = Unit

        override fun toString(): String = buffer.toString()
    }
}
