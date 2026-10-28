package org.nivora.launcher

import android.app.Application
import org.nivora.launcher.diagnostics.CrashReportStore
import org.nivora.launcher.diagnostics.NivoraLogger
import kotlin.system.exitProcess

class NivoraApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                CrashReportStore.save(this, thread, throwable)
            } catch (reportFailure: Throwable) {
                // Never let a reporting failure replace the original crash.
                NivoraLogger.error(
                    "Crash reporting failed (${reportFailure.javaClass.simpleName})"
                )
            }

            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            }
        }
    }
}
