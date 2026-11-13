package com.uzuu.signbridge.ui.theme

import org.junit.Assert.assertNotNull
import org.junit.Test

class ThemeTest {
    @Test
    fun themeColors_areDefined() {
        assertNotNull(PrimaryBlue)
        assertNotNull(HighlightYellow)
        assertNotNull(Typography)
    }
}
