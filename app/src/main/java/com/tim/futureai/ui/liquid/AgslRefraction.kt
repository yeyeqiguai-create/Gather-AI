package com.tim.futureai.ui.liquid

/**
 * AGSL (RuntimeShader, API 33+) edge refraction for the LIQUID glass material.
 *
 * The shader samples the panel's own content (the translated background layer)
 * and displaces UVs outward within a band along the panel edge, producing a
 * magnification/refraction approximation. Applied via
 * RenderEffect.createRuntimeShaderEffect, so the displacement happens before
 * the separate blur node. Falls back to blur-only below API 33 or if shader
 * compilation fails.
 */
internal const val AGSL_REFRACTION = """
uniform shader uContent;
uniform float2 uSize;
uniform float uBand;
uniform float uStrength;
half4 main(float2 fragCoord) {
    float2 edgeDist = min(fragCoord, uSize - fragCoord);
    float d = min(edgeDist.x, edgeDist.y);
    if (d >= uBand) {
        return uContent.eval(fragCoord);
    }
    float t = 1.0 - d / uBand;
    t = t * t;
    float2 halfSize = uSize * 0.5;
    float2 fromCenter = fragCoord - halfSize;
    float len = length(fromCenter);
    float2 dir = len > 0.5 ? fromCenter / len : float2(0.0, 1.0);
    float2 offset = dir * (t * uBand * uStrength);
    return uContent.eval(fragCoord + offset);
}
"""
