package com.tim.futureai.ui.liquid

/** Resource policy shared by the stage and its panels, independent of Android APIs. */
internal object GlassRenderPolicy {
    fun recordsSource(sdkInt: Int, prefs: GlassRenderPrefs): Boolean =
        sdkInt >= 31 && !prefs.lowPerformance && prefs.intensity > 0.01f

    fun surfaceMode(
        sdkInt: Int,
        prefs: GlassRenderPrefs,
        hasSource: Boolean,
        blurDp: Float,
        refractionBandDp: Float,
    ): GlassRenderMode = when {
        !hasSource || !recordsSource(sdkInt, prefs) || blurDp <= 0f -> GlassRenderMode.TINT
        sdkInt >= 33 && refractionBandDp > 0f -> GlassRenderMode.REFRACTION
        else -> GlassRenderMode.BLUR
    }
}

internal enum class GlassRenderMode { TINT, BLUR, REFRACTION }
