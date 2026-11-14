# System Theme Adaptation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable SignBridge to automatically follow the phone's system theme configuration (Dark / Light mode) with high-contrast color palettes and adaptive system bars.

**Architecture:** Define dedicated `LightColors` alongside existing `DarkColors` in `AppTheme.kt` and `Color.kt`. Configure `AppTheme` composable with `darkTheme: Boolean = isSystemInDarkTheme()` and support optional dynamic color. Update `MainActivity.kt`'s `enableEdgeToEdge()` configuration to automatically adapt status and navigation bar icon contrast to light/dark system mode.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, AndroidX Activity Compose, Robolectric, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-11-14-system-theme-adaptation-design.md`

## Global Constraints
- Follow package boundaries: `ui/theme` and `core`, `data`, `domain`, `feature`, `ui` (per `AGENTS.md`).
- Retain exact color contrast and accessibility for deaf & hearing communication.
- Backward compatibility: Existing color constants (`Background`, `Surface`, `Accent`, `Recording`, etc.) remain accessible for existing components.
- Do not introduce external dependencies or manual DI changes for simple UI theming.

## Review Focus
1. Phone in Light mode: App uses `LightColors` with clean neutral background and readable text.
2. Phone in Dark mode: App uses `DarkColors` matching previous established charcoal dark surfaces.
3. System bars (status & navigation bar): Icons change contrast (dark icons on light background, light icons on dark background).
4. Previews: Light and dark previews render without runtime crashes.
5. Robolectric unit test: Confirms `AppTheme` correctly provides the appropriate color scheme when `darkTheme` is toggled.

---

### Task 1: Light and Dark Color Palettes and AppTheme Adaptation

**Files:**
- Modify: `app/src/main/java/com/uzuu/signbridge/ui/theme/Color.kt`
- Modify: `app/src/main/java/com/uzuu/signbridge/ui/theme/AppTheme.kt`
- Test: `app/src/test/java/com/uzuu/signbridge/ui/theme/AppThemeTest.kt`

**Interfaces:**
- Produces:
  - `LightColors: ColorScheme`
  - `DarkColors: ColorScheme`
  - `AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = false, content: @Composable () -> Unit)`

- [ ] **Step 1: Write the failing unit test for AppTheme**

Create `app/src/test/java/com/uzuu/signbridge/ui/theme/AppThemeTest.kt`:
```kotlin
package com.uzuu.signbridge.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppThemeTest {

    @Test
    fun appTheme_darkThemeTrue_appliesDarkColors() {
        assertEquals(DarkBackground, DarkColors.background)
        assertEquals(DarkSurface, DarkColors.surface)
    }

    @Test
    fun appTheme_darkThemeFalse_appliesLightColors() {
        assertEquals(LightBackground, LightColors.background)
        assertEquals(LightSurface, LightColors.surface)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.uzuu.signbridge.ui.theme.AppThemeTest"`
Expected: FAIL due to unresolved references `DarkBackground`, `LightBackground`, `LightColors`, etc.

- [ ] **Step 3: Implement Light/Dark Color tokens and AppTheme**

1. In `Color.kt`, add:
   - `DarkBackground`, `DarkSurface`, `DarkSurfaceRaised`, `DarkTextPrimary`, `DarkTextSecondary`, `DarkAccent`, `DarkOnAccent`, `DarkSelectedSurface`, `DarkOnSelectedSurface`, `DarkOutline`, `DarkDivider`, `DarkError`, `DarkErrorSurface`, `DarkOnErrorSurface`.
   - Backward-compatible aliases: `Background = DarkBackground`, `Surface = DarkSurface`, etc.
   - `LightBackground`, `LightSurface`, `LightSurfaceRaised`, `LightTextPrimary`, `LightTextSecondary`, `LightAccent`, `LightOnAccent`, `LightSelectedSurface`, `LightOnSelectedSurface`, `LightOutline`, `LightDivider`, `LightError`, `LightErrorSurface`, `LightOnErrorSurface`.
   - `Recording` and `OnRecording` preserved.

2. In `AppTheme.kt`:
   - Define `DarkColors = darkColorScheme(...)` using dark tokens.
   - Define `LightColors = lightColorScheme(...)` using light tokens.
   - Update `AppTheme` signature:
     ```kotlin
     @Composable
     fun AppTheme(
         darkTheme: Boolean = isSystemInDarkTheme(),
         dynamicColor: Boolean = false,
         content: @Composable () -> Unit,
     ) {
         val colorScheme = when {
             dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                 val context = LocalContext.current
                 if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
             }
             darkTheme -> DarkColors
             else -> LightColors
         }
         MaterialTheme(
             colorScheme = colorScheme,
             typography = AppTypography,
             shapes = Shapes(
                 small = RoundedCornerShape(Dimens.CornerRadius),
                 medium = RoundedCornerShape(Dimens.CornerRadius),
                 large = RoundedCornerShape(Dimens.CornerRadius),
             ),
             content = content,
         )
     }
     ```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat testDebugUnitTest --tests "com.uzuu.signbridge.ui.theme.AppThemeTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/uzuu/signbridge/ui/theme/ app/src/test/java/com/uzuu/signbridge/ui/theme/AppThemeTest.kt
git commit -m "feat(theme): add light color scheme and system theme support"
```

---

### Task 2: Edge-to-Edge System Bar Adaptation and Compose Previews

**Files:**
- Modify: `app/src/main/java/com/uzuu/signbridge/MainActivity.kt`
- Modify: `app/src/main/java/com/uzuu/signbridge/ui/theme/AppTheme.kt`

**Interfaces:**
- Consumes:
  - `AppTheme`
  - `enableEdgeToEdge` with dynamic `SystemBarStyle.auto`

- [ ] **Step 1: Update MainActivity.kt for adaptive status & navigation bars**

Change `enableEdgeToEdge()` in `MainActivity.kt`:
```kotlin
enableEdgeToEdge(
    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
)
```
This ensures Android automatically renders dark icons in light mode and light icons in dark mode.

- [ ] **Step 2: Add Light and Dark Previews to AppTheme.kt**

Add preview composables verifying both Light and Dark renderings in `AppTheme.kt`:
```kotlin
@Preview(name = "Light Theme", showBackground = true)
@Composable
private fun LightThemePreview() {
    AppTheme(darkTheme = false) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Text("SignBridge Light", color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Preview(name = "Dark Theme", showBackground = true)
@Composable
private fun DarkThemePreview() {
    AppTheme(darkTheme = true) {
        Surface(color = MaterialTheme.colorScheme.background) {
            Text("SignBridge Dark", color = MaterialTheme.colorScheme.onBackground)
        }
    }
}
```

- [ ] **Step 3: Run full verification test suite**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: BUILD SUCCESSFUL with all unit tests passing.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/uzuu/signbridge/MainActivity.kt app/src/main/java/com/uzuu/signbridge/ui/theme/AppTheme.kt
git commit -m "feat(theme): adapt system bars to dark/light mode and add previews"
```
