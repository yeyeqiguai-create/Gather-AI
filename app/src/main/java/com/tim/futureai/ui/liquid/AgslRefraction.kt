package com.tim.futureai.ui.liquid

internal const val AGSL_LEGACY_REFRACTION = """
uniform shader uContent;
uniform float2 uSize;
uniform float uBand;
uniform float uStrength;
half4 main(float2 p) {
    float d = min(min(p.x, p.y), min(uSize.x - p.x, uSize.y - p.y));
    float t = clamp(1.0 - d / max(1.0, uBand), 0.0, 1.0);
    float2 v = p - uSize * 0.5;
    float2 dir = v / max(0.5, length(v));
    return uContent.eval(p + dir * t * t * uBand * uStrength);
}
"""

/** Rounded SDF height field and Snell refraction of the recorded background. */
internal const val AGSL_REFRACTION = """
uniform shader uContent;
uniform float2 uSize;
uniform float uBand;
uniform float uStrength;
uniform float uCorner;
uniform float2 uPointer;
uniform float uPressure;
half4 main(float2 fragCoord) {
    float2 p = fragCoord - uSize * 0.5;
    float radius = min(uCorner, min(uSize.x, uSize.y) * 0.49);
    float2 q = abs(p) - (uSize * 0.5 - radius);
    float2 rounded = max(q, float2(0.0)) + step(q.yx, q) * 0.001;
    float len = sqrt(dot(rounded, rounded));
    float sdf = min(max(q.x, q.y), 0.0) + len - radius;
    float band = min(uBand, min(uSize.x, uSize.y) * 0.42);
    float edge = clamp(1.0 + sdf / max(1.0, band), 0.0, 1.0);
    float2 outward = rounded / len * sign(p);
    float2 touch = (fragCoord - uPointer * uSize) / max(70.0, uBand * 2.5);
    float influence = clamp(1.0 - dot(touch, touch), 0.0, 1.0);
    float2 slope = outward * edge * edge * 2.2 - touch * influence * uPressure * 0.75;
    float3 normal = normalize(float3(slope, 1.0));
    float eta = 0.69;
    float cosine = normal.z;
    float k = 1.0 - eta * eta * (1.0 - cosine * cosine);
    float3 ray = float3(0.0, 0.0, -eta) + (eta * cosine - sqrt(k)) * normal;
    float thickness = uBand * (0.46 + uStrength * 0.50) * (1.0 + uPressure * 0.13);
    float2 offset = ray.xy / max(0.18, -ray.z) * thickness;
    float2 bent = fragCoord + offset;
    half4 color = uContent.eval(bent);
    color.r = uContent.eval(bent + offset * 0.012).r;
    color.b = uContent.eval(bent - offset * 0.012).b;
    float3 light = normalize(float3((uPointer - 0.5) * 0.95, 0.85));
    float highlight = clamp(dot(normal, light), 0.0, 1.0);
    highlight *= highlight; highlight *= highlight; highlight *= highlight; highlight *= highlight;
    float grazing = 1.0 - normal.z;
    float fresnel = grazing * grazing * grazing;
    color.rgb += half3(edge * (highlight * 0.30 + fresnel * 0.15));
    color.rgb *= half(1.0 - edge * (1.0 - edge) * 0.18);
    return color;
}
"""
