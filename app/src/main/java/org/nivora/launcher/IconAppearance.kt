package org.nivora.launcher

import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix

/**
 * Appearance treatments for app icons rendered inside Nivora Launcher.
 *
 * These filters approximate appearance styles; they do not modify the
 * installed apps' original icons or Nivora Launcher's own app icon.
 */
internal enum class IconAppearance(
    val title: String,
    val storedValue: String
) {
    SYSTEM_DEFAULT("System Default", "system_default"),
    ORIGINAL("Original", "original"),
    LIGHT("Light", "light"),
    DARK("Dark", "dark"),
    CLEAR("Clear", "clear"),
    TINTED("Tinted", "tinted");

    fun colorFilter(isSystemDark: Boolean = true): ColorFilter? =
        when (this) {
            SYSTEM_DEFAULT -> if (isSystemDark) darkFilter() else lightFilter()
            ORIGINAL -> null
            LIGHT -> lightFilter()
            DARK -> darkFilter()

            CLEAR -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, 0f,
                        0f, 1f, 0f, 0f, 0f,
                        0f, 0f, 1f, 0f, 0f,
                        0f, 0f, 0f, 0.76f, 0f
                    )
                )
            )

            TINTED -> ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        0.22f, 0.50f, 0.08f, 0f, 0f,
                        0.18f, 0.42f, 0.07f, 0f, 0f,
                        0.25f, 0.56f, 0.09f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }

    private fun lightFilter(): ColorFilter =
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    1.12f, 0f, 0f, 0f, 12f,
                    0f, 1.12f, 0f, 0f, 12f,
                    0f, 0f, 1.12f, 0f, 12f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )

    private fun darkFilter(): ColorFilter =
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.62f, 0f, 0f, 0f, 0f,
                    0f, 0.62f, 0f, 0f, 0f,
                    0f, 0f, 0.62f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )

    companion object {
        fun fromStoredValue(value: String?): IconAppearance =
            values().firstOrNull { it.storedValue == value } ?: SYSTEM_DEFAULT
    }
}
