#version 330
#extension GL_ARB_separate_shader_objects : require

// Wraith ink: a mark that spreads by time rather than by scale. The mask's alpha holds when the ink reaches each pixel and its red when the
// hole opens there, both 1 for never. The vertex color carries the timeline: red how far the ink has spread, green how far the hole has opened,
// blue how brightly the fronts glow, and alpha how to draw it: 0 a preview that draws only the glowing outline, about half a window whose
// hole is left open so the tunnel behind it shows, 1 a mark whose hole shows the realm.
// MOUTH draws the other side of a hole, from inside a Wraith Veil tunnel: the realm's wall around it.

#include <minecraft:globals.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
uniform sampler2D DissolveMaskSampler;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 4) in vec4 lightMapColor;
layout(location = 6) in vec2 texCoord0;

layout(location = 0) out vec4 fragColor;

const vec3  INK         = vec3(0.030, 0.016, 0.042);
const vec3  INK_SHEEN   = vec3(0.150, 0.070, 0.220);
const vec3  RIM_HOT     = vec3(0.880, 0.780, 1.000);
const vec3  RIM_COOL    = vec3(0.420, 0.150, 0.820);
const vec3  HOLE_GLOW   = vec3(0.620, 0.300, 1.000);
// How far ahead of the ink its glowing front runs, and the white-hot part of it, in mask time
const float RIM_WIDTH   = 0.07;
const float HOT_WIDTH   = 0.022;
// How far the hole's smouldering edge reaches into the ink, in mask time
const float HOLE_RIM    = 0.11;
// The red channel's time at which the hole is fully open; beyond it lies the ink its edge glows into
const float HOLE_END    = 0.8;
// Realm smoke: texture repeats per block, and how fast its two layers drift in repeats per tick. 24000 times each drift is whole, so the
// wrap of GameTime every day never jumps
const float SMOKE_SCALE = 0.75;
const float DRIFT_A     = 1.0 / 600.0;
const float DRIFT_B     = 1.0 / 400.0;
const float MARK_BLOCKS = 2.0;

vec3 realm(vec2 uv, float ticks) {
    vec3 a = texture(Sampler0, fract(uv * SMOKE_SCALE + vec2(0.0, ticks * DRIFT_A))).rgb;
    vec3 b = texture(Sampler0, fract(uv * SMOKE_SCALE * 1.7 + vec2(ticks * DRIFT_B, -ticks * DRIFT_A))).rgb;
    return a * (0.55 + 3.0 * b);
}

void main() {
    vec4 mask = texture(DissolveMaskSampler, texCoord0);
    float inkTime = mask.a;
    float holeTime = mask.r < 0.999 ? mask.r : 2.0;
    float spread = vertexColor.r;
    float open = vertexColor.g * HOLE_END;
    float glow = vertexColor.b;
    bool preview = vertexColor.a < 0.25;
    bool window = vertexColor.a >= 0.25 && vertexColor.a < 0.75;

    float ticks = GameTime * 24000.0;
    // Square texels whatever the mask's shape, in about block units
    vec2 size = vec2(textureSize(DissolveMaskSampler, 0));
    vec2 uv = texCoord0 * size / size.x * MARK_BLOCKS;
    float flicker = 0.75 + 0.25 * sin(ticks * 0.35 + texture(Sampler0, fract(uv * 0.5)).r * 40.0);

    vec3 color;
#ifdef MOUTH
    if (holeTime < open)
        discard;
    color = realm(uv, ticks);
    if (open > 0.0 && holeTime < open + HOLE_RIM)
        color = mix(color, HOLE_GLOW * flicker, (1.0 - (holeTime - open) / HOLE_RIM) * glow);
#else
    if (inkTime >= 0.999 || inkTime > spread + RIM_WIDTH)
        discard;
    if (holeTime < open) {
        if (window)
            discard;
        color = realm(uv, ticks);
    } else if (inkTime > spread) {
        if (glow <= 0.0)
            discard;
        float t = (inkTime - spread) / RIM_WIDTH;
        color = mix(RIM_HOT, RIM_COOL, smoothstep(HOT_WIDTH / RIM_WIDTH, 1.0, t)) * glow;
    } else {
        if (preview)
            discard;
        float sheen = texture(Sampler0, fract(uv * 1.3)).r * 4.0;
        color = mix(INK, INK_SHEEN, clamp(sheen, 0.0, 1.0) * 0.5) * lightMapColor.rgb;
        if (open > 0.0 && holeTime < open + HOLE_RIM)
            color = mix(color, HOLE_GLOW * flicker, (1.0 - (holeTime - open) / HOLE_RIM) * glow);
    }
#endif

    fragColor = apply_fog(vec4(color, 1.0), sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd,
            FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
