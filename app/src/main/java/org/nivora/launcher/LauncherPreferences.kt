package org.nivora.launcher

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.nivoraDataStore by preferencesDataStore(
    name = "nivora_preferences"
)

/**
 * Stores small launcher preferences locally on this device.
 * Values remain available after the app process is restarted.
 */
class LauncherPreferences(private val context: Context) {

    private companion object {
        val SHOW_APP_LABELS = booleanPreferencesKey("show_app_labels")
    }

    val showAppLabels: Flow<Boolean> =
        context.nivoraDataStore.data.map { preferences ->
            preferences[SHOW_APP_LABELS] ?: true
        }

    suspend fun setShowAppLabels(enabled: Boolean) {
        context.nivoraDataStore.edit { preferences ->
            preferences[SHOW_APP_LABELS] = enabled
        }
    }
}
