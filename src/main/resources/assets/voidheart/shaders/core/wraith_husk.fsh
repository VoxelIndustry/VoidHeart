#version 330
#extension GL_ARB_separate_shader_objects : require

// A raised wraith's husk: the dead mob's model refilled with wraith ink. Its own texture only shows as a faint violet sheen, void smoke drifts up
// inside it, a pale rim glows where its faces turn away from the eye, and its lower half frays into holes that come and go.
// The vertex color's red is how much more it frays, while it rises out of the ground or sinks back.

#include <minecraft:globals.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
uniform sampler2D DissolveMaskSampler;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 6) in vec2 texCoord0;
layout(location = 7) in vec2 body;
layout(location = 8) in vec3 viewPos;
layout(location = 9) in vec3 faceNormal;

layout(location = 0) out vec4 fragColor;

const vec3  INK          = vec3(0.030, 0.016, 0.042);
const vec3  INK_SHEEN    = vec3(0.200, 0.090, 0.310);
const vec3  SMOKE_TINT   = vec3(0.420, 0.200, 0.700);
const vec3  RIM_HOT      = vec3(0.880, 0.780, 1.000);
const vec3  RIM_COOL     = vec3(0.420, 0.150, 0.820);
// How much of the mob's own texture shows through the ink
const float SHEEN        = 0.75;
// The smoke's scale on the model's texture, and how fast its two layers drift in repeats per tick. 24000 times each is whole, so the daily wrap
// of GameTime never jumps
const float SMOKE_SCALE  = 3.0;
const float DRIFT_A      = 1.0 / 300.0;
const float DRIFT_B      = 1.0 / 200.0;
const float SMOKE        = 0.5;
// The rim: only faces seen nearly edge-on glow, since a box model's faces are flat and a softer rim would tint whole faces. Below this much
// facing the eye a face starts to glow, and at its edge-on most it is this bright
const float RIM_FROM     = 0.3;
const float RIM_STRENGTH = 0.9;
// Fraying: the share of the body from the feet up that frays, the most it eats away at the feet, and how fast its holes shift
const float FRAY_HEIGHT  = 0.55;
const float FRAY_FEET    = 0.55;
const float FRAY_DRIFT   = 1.0 / 240.0;
// Where a hole's edge glows
const float HOLE_RIM     = 0.04;
const float HOLE_GLOW    = 0.55;

float smoke(vec2 uv, float ticks) {
    float a = texture(DissolveMaskSampler, fract(uv * SMOKE_SCALE + vec2(0.0, -ticks * DRIFT_A))).r;
    float b = texture(DissolveMaskSampler, fract(uv * SMOKE_SCALE * 1.7 + vec2(ticks * DRIFT_B, -ticks * DRIFT_A))).r;
    return clamp(a * (0.55 + 3.0 * b), 0.0, 1.0);
}

void main() {
    vec4 skin = texture(Sampler0, texCoord0);
    if (skin.a < 0.1)
        discard;

    float ticks = GameTime * 24000.0;
    float height = body.y > 0.0 ? clamp((viewPos.y - body.x) / body.y, 0.0, 1.0) : 1.0;

    // Holes eat in from the feet, more of them while it rises or sinks
    float fray = FRAY_FEET * (1.0 - smoothstep(0.0, FRAY_HEIGHT, height)) + vertexColor.r;
    float noise = texture(DissolveMaskSampler, fract(texCoord0 * 2.3 + vec2(ticks * FRAY_DRIFT, -ticks * FRAY_DRIFT * 0.7))).r * 4.0;
    noise = clamp(noise, 0.0, 1.0);
    if (noise < fray)
        discard;

    float shade = dot(skin.rgb, vec3(0.299, 0.587, 0.114));
    vec3 color = mix(INK, INK_SHEEN, shade * SHEEN);
    color += SMOKE_TINT * smoke(texCoord0, ticks) * SMOKE;

    // The face's own normal from how its place changes across the screen, so it is in the same space as the view whatever the model's normals are
    vec3 normal = normalize(cross(dFdx(viewPos), dFdy(viewPos)));
    vec3 toEye = normalize(-viewPos);
    float facing = abs(dot(normal, toEye));
    float rim = (1.0 - smoothstep(0.0, RIM_FROM, facing)) * RIM_STRENGTH;
    color += mix(RIM_COOL, RIM_HOT, rim) * rim;

    if (noise < fray + HOLE_RIM)
        color = mix(color, RIM_COOL * HOLE_GLOW, 1.0 - (noise - fray) / HOLE_RIM);

    fragColor = apply_fog(vec4(color, 1.0), sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd,
            FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
