package org.nivora.launcher

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
        val DOCK_APP_PACKAGES = stringPreferencesKey("dock_app_packages")
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

    /** Null means the user has not customized the automatic dock yet. */
    val dockAppPackages: Flow<List<String>?> =
        context.nivoraDataStore.data.map { preferences ->
            preferences[DOCK_APP_PACKAGES]?.split("|")
                ?.filter { it.isNotBlank() }
        }

    suspend fun setDockAppPackages(packageNames: List<String>) {
        context.nivoraDataStore.edit { preferences ->
            preferences[DOCK_APP_PACKAGES] =
                packageNames.distinct().take(4).joinToString("|")
        }
    }

    suspend fun resetDockAppPackagesToAutomatic() {
        context.nivoraDataStore.edit { preferences ->
            preferences.remove(DOCK_APP_PACKAGES)
        }
    }
}
