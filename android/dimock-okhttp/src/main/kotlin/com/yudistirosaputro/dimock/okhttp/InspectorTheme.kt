package com.yudistirosaputro.dimock.okhttp

/**
 * Appearance of the in-app inspector. `System` follows the host app's light/dark setting.
 * Settable in code via `Dimock.Config(inspectorTheme = ...)` or per variant with
 * `<meta-data android:name="com.yudistirosaputro.dimock.INSPECTOR_THEME" android:value="dark" />`.
 */
enum class InspectorTheme { System, Dark, Light }
