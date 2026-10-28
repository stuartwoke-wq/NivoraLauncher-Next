package org.nivora.launcher.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportFormatterTest {
    @Test
    fun reportIncludesDiagnosticMetadataAndStackTrace() {
        val report = CrashReportFormatter.format(
            timestamp = "2026-10-28T12-00-00.000Z",
            appVersion = "0.1.0",
            androidVersion = "13 (API 33)",
            deviceModel = "Example Device",
            threadName = "main",
            stackTrace = "java.lang.IllegalStateException: test failure"
        )

        assertTrue(report.contains("Nivora Launcher crash report"))
        assertTrue(report.contains("App version: 0.1.0"))
        assertTrue(report.contains("Android version: 13 (API 33)"))
        assertTrue(report.contains("Device model: Example Device"))
        assertTrue(report.contains("Thread: main"))
        assertTrue(report.contains("java.lang.IllegalStateException: test failure"))
    }

    @Test
    fun oversizedStackTraceIsTruncated() {
        val report = CrashReportFormatter.format(
            timestamp = "now",
            appVersion = "test",
            androidVersion = "test",
            deviceModel = "test",
            threadName = "main",
            stackTrace = "x".repeat(CrashReportFormatter.MAX_STACK_TRACE_CHARS + 100)
        )

        assertTrue(report.contains("[stack trace truncated]"))
        assertTrue(report.length < CrashReportFormatter.MAX_STACK_TRACE_CHARS + 500)
    }

    @Test
    fun capturedStackTraceIsBoundedForLargeExceptions() {
        val throwable = IllegalStateException("large test exception")
        throwable.stackTrace = Array(10_000) { index ->
            StackTraceElement("ExampleClass", "method", "Example.kt", index)
        }

        val trace = CrashReportFormatter.captureStackTrace(throwable)

        assertTrue(trace.contains("[stack trace truncated]"))
        assertTrue(trace.length <= CrashReportFormatter.MAX_STACK_TRACE_CHARS)
    }

    @Test
    fun reportDoesNotAddNetworkOrSharingInstructions() {
        val report = CrashReportFormatter.format(
            timestamp = "now",
            appVersion = "test",
            androidVersion = "test",
            deviceModel = "test",
            threadName = "main",
            stackTrace = "test"
        )

        assertFalse(report.contains("http://"))
        assertFalse(report.contains("https://"))
    }
}
