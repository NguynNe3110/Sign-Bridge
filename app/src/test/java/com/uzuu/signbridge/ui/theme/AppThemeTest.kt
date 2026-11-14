package com.uzuu.signbridge.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppThemeTest {

    @Test
    fun appTheme_tokensConfiguredCorrectly() {
        assertEquals(DarkBackground, DarkColors.background)
        assertEquals(DarkSurface, DarkColors.surface)
        assertEquals(DarkAccent, DarkColors.primary)
        assertEquals(LightBackground, LightColors.background)
        assertEquals(LightSurface, LightColors.surface)
        assertEquals(LightAccent, LightColors.primary)
    }

    @Test
    fun appTheme_darkThemeTrue_providesDarkColorScheme() {
        val scheme = appColorScheme(darkTheme = true, dynamicColor = false)
        assertEquals(DarkColors, scheme)
        assertEquals(DarkBackground, scheme.background)
        assertEquals(DarkTextPrimary, scheme.onBackground)
    }

    @Test
    fun appTheme_darkThemeFalse_providesLightColorScheme() {
        val scheme = appColorScheme(darkTheme = false, dynamicColor = false)
        assertEquals(LightColors, scheme)
        assertEquals(LightBackground, scheme.background)
        assertEquals(LightTextPrimary, scheme.onBackground)
        assertNotEquals(DarkColors.background, scheme.background)
    }
}
