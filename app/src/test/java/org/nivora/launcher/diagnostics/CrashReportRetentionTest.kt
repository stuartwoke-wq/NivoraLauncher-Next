package org.nivora.launcher.diagnostics

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportRetentionTest {

    @Test
    fun pruneKeepsFiveNewestReports() {
        val directory = Files.createTempDirectory("nivora-retention-test").toFile()

        try {
            val reports = (1..7).map { index ->
                File(directory, "crash-$index.txt").apply {
                    writeText("report $index")
                    setLastModified(index * 1_000L)
                }
            }

            CrashReportRetention.prune(directory)

            val remaining = directory.listFiles { file ->
                file.isFile && file.name.endsWith(".txt")
            } ?: emptyArray()

            assertEquals(CrashReportRetention.MAX_REPORTS, remaining.size)
            assertFalse(reports[0].exists())
            assertFalse(reports[1].exists())

            for (index in 2..6) {
                assertTrue(reports[index].exists())
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun prunePreservesUnrelatedFiles() {
        val directory = Files.createTempDirectory("nivora-retention-test").toFile()

        try {
            val unrelated = File(directory, "notes.log").apply {
                writeText("keep this file")
            }

            (1..6).forEach { index ->
                File(directory, "crash-$index.txt").apply {
                    writeText("report $index")
                    setLastModified(index * 1_000L)
                }
            }

            CrashReportRetention.prune(directory)

            assertTrue(unrelated.exists())
            assertEquals("keep this file", unrelated.readText())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun pruneHandlesMissingDirectory() {
        val parent = Files.createTempDirectory("nivora-retention-test").toFile()
        val missingDirectory = File(parent, "does-not-exist")

        try {
            CrashReportRetention.prune(missingDirectory)
            assertFalse(missingDirectory.exists())
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test
    fun prunePreservesReportsWhenFewerThanLimitExist() {
        val directory = Files.createTempDirectory("nivora-retention-test").toFile()

        try {
            val reports = (1..3).map { index ->
                File(directory, "crash-$index.txt").apply {
                    writeText("report $index")
                }
            }

            CrashReportRetention.prune(directory)

            assertTrue(reports.all { it.exists() })
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun pruneUsesFilenameToBreakEqualTimestampTies() {
        val directory = Files.createTempDirectory("nivora-retention-test").toFile()

        try {
            val reports = ('a'..'f').map { letter ->
                File(directory, "crash-$letter.txt").apply {
                    writeText("report $letter")
                    check(setLastModified(10_000L)) {
                        "Could not set test timestamp for $name"
                    }
                }
            }

            assertTrue(reports.all { it.lastModified() == reports.first().lastModified() })

            CrashReportRetention.prune(directory)

            // With equal timestamps, descending filename order is retained.
            assertFalse(reports[0].exists())
            assertTrue(reports.drop(1).all { it.exists() })
        } finally {
            directory.deleteRecursively()
        }
    }
}
