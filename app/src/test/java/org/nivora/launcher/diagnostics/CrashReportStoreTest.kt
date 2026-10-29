package org.nivora.launcher.diagnostics

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportStoreTest {

    @Test
    fun listReportsIncludesOnlyRegularTxtFiles() {
        val directory = Files.createTempDirectory("nivora-store-test").toFile()
        try {
            val report = File(directory, "crash-one.txt").apply {
                writeText("private report contents")
            }
            val unrelated = File(directory, "notes.log").apply {
                writeText("not a report")
            }
            val nestedDirectory = File(directory, "nested.txt").apply {
                mkdir()
            }

            val results = CrashReportStore.listReportsIn(directory)

            assertEquals(listOf(report.name), results.map { it.fileName })
            assertFalse(results.any { it.fileName == unrelated.name })
            assertFalse(results.any { it.fileName == nestedDirectory.name })
            assertEquals(report.length(), results.single().sizeBytes)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun listReportsSortsNewestFirstAndBreaksTiesByDescendingName() {
        val directory = Files.createTempDirectory("nivora-store-test").toFile()
        try {
            val older = File(directory, "crash-z.txt").apply {
                writeText("older")
                check(setLastModified(1_000L))
            }
            val tiedA = File(directory, "crash-a.txt").apply {
                writeText("same time")
                check(setLastModified(2_000L))
            }
            val tiedB = File(directory, "crash-b.txt").apply {
                writeText("same time")
                check(setLastModified(2_000L))
            }

            val results = CrashReportStore.listReportsIn(directory)

            assertEquals(
                listOf(tiedB.name, tiedA.name, older.name),
                results.map { it.fileName }
            )
            assertTrue(results.first().lastModified >= results.last().lastModified)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun listReportsReturnsAtMostRetentionLimit() {
        val directory = Files.createTempDirectory("nivora-store-test").toFile()
        try {
            (1..7).forEach { index ->
                File(directory, "crash-$index.txt").apply {
                    writeText("report $index")
                    check(setLastModified(index * 1_000L))
                }
            }

            val results = CrashReportStore.listReportsIn(directory)

            assertEquals(CrashReportRetention.MAX_REPORTS, results.size)
            assertEquals(
                listOf(
                    "crash-7.txt",
                    "crash-6.txt",
                    "crash-5.txt",
                    "crash-4.txt",
                    "crash-3.txt"
                ),
                results.map { it.fileName }
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun listReportsReturnsEmptyForMissingDirectory() {
        val parent = Files.createTempDirectory("nivora-store-test").toFile()
        try {
            val missing = File(parent, "missing")
            assertTrue(CrashReportStore.listReportsIn(missing).isEmpty())
            assertFalse(missing.exists())
        } finally {
            parent.deleteRecursively()
        }
    }
}
