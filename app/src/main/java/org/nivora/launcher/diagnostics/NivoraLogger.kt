package org.nivora.launcher.diagnostics

import android.util.Log
import org.nivora.launcher.BuildConfig

/**
 * Small Android-log wrapper. Debug detail is disabled in release builds.
 * Never pass app labels, search queries, or other user content to these methods.
 */
object NivoraLogger {
    private const val TAG = "Nivora"

    fun debug(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    fun info(message: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, message)
    }

    fun warning(message: String, error: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (error == null) Log.w(TAG, message)
            else Log.w(TAG, message, error)
        }
    }

    fun error(message: String, error: Throwable? = null) {
        if (error == null) Log.e(TAG, message)
        else Log.e(TAG, message, error)
    }
}
