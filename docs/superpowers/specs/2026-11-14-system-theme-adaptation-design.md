# System Theme Adaptation Design

## 1. Intent & Context
Currently, SignBridge hardcodes a dark color scheme in `AppTheme.kt` and dark edge-to-edge system bars in `MainActivity.kt`.
When users configure their phone to Light mode, the app remains dark, conflicting with the user's system preferences.

This feature enables SignBridge to automatically follow the phone's system theme configuration (`isSystemInDarkTheme()`). It introduces a clean, high-contrast Light Color Scheme tailored for accessible two-way communication between deaf and hearing users, and dynamically adapts the system status and navigation bars.

## 2. Goals & Non-Goals
### Goals
- Automatically adapt application theme to the device's system setting (Dark / Light mode).
- Provide a dedicated, high-contrast, accessible `LightColorScheme` matching SignBridge's design language.
- Ensure status bar and navigation bar contrast adapt automatically to the active theme via `enableEdgeToEdge`.
- Provide previews and tests verifying that `AppTheme` correctly provides light or dark color tokens based on the `darkTheme` flag.

### Non-Goals
- Adding complex manual theme switching dialogs or persistent theme preferences in DataStore/SharedPreferences at this time (the requirement is strictly to follow phone configuration).
- Modifying business logic, repository layers, or API communication.

## 3. Architecture & Token Mapping

### Color Tokens in `Color.kt`
#### Dark Palette (existing, preserved)
- `DarkBackground = Color(0xFF151719)`
- `DarkSurface = Color(0xFF202326)`
- `DarkSurfaceRaised = Color(0xFF2B2F33)`
- `DarkTextPrimary = Color(0xFFF0F1F2)`
- `DarkTextSecondary = Color(0xFFB9BEC3)`
- `DarkAccent = Color(0xFFC4D2DC)`
- `DarkOnAccent = Color(0xFF19242C)`
- `DarkSelectedSurface = Color(0xFF35444F)`
- `DarkOnSelectedSurface = Color(0xFFE2EBF1)`
- `DarkOutline = Color(0xFF858D94)`
- `DarkDivider = Color(0xFF42484E)`
- `DarkError = Color(0xFFFFB4AB)`
- `DarkErrorSurface = Color(0xFF49302F)`
- `DarkOnErrorSurface = Color(0xFFFFDAD6)`

#### Light Palette (new)
- `LightBackground = Color(0xFFF7F9FB)`
- `LightSurface = Color(0xFFFFFFFF)`
- `LightSurfaceRaised = Color(0xFFEEF2F6)`
- `LightTextPrimary = Color(0xFF191C20)`
- `LightTextSecondary = Color(0xFF5A626A)`
- `LightAccent = Color(0xFF2B4C68)`
- `LightOnAccent = Color(0xFFFFFFFF)`
- `LightSelectedSurface = Color(0xFFD8E5F0)`
- `LightOnSelectedSurface = Color(0xFF122433)`
- `LightOutline = Color(0xFF737B83)`
- `LightDivider = Color(0xFFD9E0E6)`
- `LightError = Color(0xFFBA1A1A)`
- `LightErrorSurface = Color(0xFFFFEDEA)`
- `LightOnErrorSurface = Color(0xFF410002)`

#### Shared Constants
- `Recording = Color(0xFF9D3834)`
- `OnRecording = Color(0xFFFFFFFF)`

### Theme Implementation in `AppTheme.kt`
- `DarkColors: ColorScheme = darkColorScheme(...)`
- `LightColors: ColorScheme = lightColorScheme(...)`
- Composable function:
  ```kotlin
  @Composable
  fun AppTheme(
      darkTheme: Boolean = isSystemInDarkTheme(),
      dynamicColor: Boolean = false,
      content: @Composable () -> Unit,
  )
  ```
- Uses `dynamicDarkColorScheme` / `dynamicLightColorScheme` on Android 12+ (API 31+) if `dynamicColor == true`.
- Otherwise selects `DarkColors` when `darkTheme == true`, or `LightColors` when `darkTheme == false`.

### Edge-to-Edge System Bar Adaptation in `MainActivity.kt`
- Update `enableEdgeToEdge()` in `MainActivity.kt` to allow automatic light/dark icon contrast for status bar and navigation bar:
  `enableEdgeToEdge(statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT))`
  or default `enableEdgeToEdge()`.
