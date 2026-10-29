package org.nivora.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconAppearanceTest {

    @Test
    fun allAppearanceOptionsHaveUniqueStoredValues() {
        val values = IconAppearance.values().map { it.storedValue }

        assertEquals(IconAppearance.values().size, values.toSet().size)
    }

    @Test
    fun everyStoredValueMapsBackToItsAppearance() {
        IconAppearance.values().forEach { appearance ->
            assertEquals(
                appearance,
                IconAppearance.fromStoredValue(appearance.storedValue)
            )
        }
    }

    @Test
    fun missingOrUnknownStoredValueDefaultsToSystemDefault() {
        assertEquals(
            IconAppearance.SYSTEM_DEFAULT,
            IconAppearance.fromStoredValue(null)
        )
        assertEquals(
            IconAppearance.SYSTEM_DEFAULT,
            IconAppearance.fromStoredValue("unknown")
        )
    }

    @Test
    fun originalAppearanceDoesNotApplyAColorFilter() {
        assertNull(IconAppearance.ORIGINAL.colorFilter())
    }

    @Test
    fun manualAppearanceOptionsReturnColorFilters() {
        assertNotNull(IconAppearance.LIGHT.colorFilter())
        assertNotNull(IconAppearance.DARK.colorFilter())
        assertNotNull(IconAppearance.CLEAR.colorFilter())
        assertNotNull(IconAppearance.TINTED.colorFilter())
    }

    @Test
    fun systemDefaultReturnsAFilterForBothSystemThemes() {
        assertNotNull(IconAppearance.SYSTEM_DEFAULT.colorFilter(isSystemDark = false))
        assertNotNull(IconAppearance.SYSTEM_DEFAULT.colorFilter(isSystemDark = true))
    }

    @Test
    fun systemDefaultUsesDifferentFiltersForLightAndDarkThemes() {
        val lightFilter =
            IconAppearance.SYSTEM_DEFAULT.colorFilter(isSystemDark = false)
        val darkFilter =
            IconAppearance.SYSTEM_DEFAULT.colorFilter(isSystemDark = true)

        assertNotNull(lightFilter)
        assertNotNull(darkFilter)
        assertFalse(lightFilter == darkFilter)
    }
}
