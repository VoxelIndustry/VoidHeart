#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <voidheart:curse_sky.glsl>

// Every star turns into an eye at its own moment, and back. CurseStateA.x runs from 0 to 1 and beyond while they open,
// y from 0 to 1 while they come to stare, z from 0 to 1 while they close.

layout(location = 0) in vec3 Position;
layout(location = 1) in vec3 Center;
layout(location = 2) in vec4 Corner;
layout(location = 3) in vec4 Seed;

layout(location = 0) out vec2 vUv;
layout(location = 1) flat out vec4 vLook;
layout(location = 2) flat out vec4 vEye;

// Half widths in blocks at the dome's radius of 100, and how much taller than wide the quad is
const float WIDTH_LEAST = 1.7;
const float WIDTH_MOST = 3.0;
const float WIDTH_BIAS = 1.6;
const float ASPECT = 0.86;
const float STARE_GROWTH = 0.16;
const float TILT_MOST = 0.3;

// Shares of the opening window: when the last eye starts, and how long one takes
const float OPEN_SPREAD = 0.56;
const float OPEN_SPAN = 0.44;
const float CLOSE_SPREAD = 0.45;
// Lids stay shut until the star has grown this far
const float LIDS_PART_FROM = 0.38;
const float LIDS_PART_TO = 0.9;
const float STAR_GONE_AT = 0.2;

// Shares of the stare window: how long one iris takes to snap, the rest being the wave climbing from the horizon to the zenith
const float STARE_SPAN = 0.14;
const float STARE_WAVE = 0.72;

const float WANDER_RATE_LEAST = 0.45;
const float WANDER_RATE_MOST = 1.3;
const float WANDER_DART_SHARE = 0.16;
const vec2 WANDER_REACH = vec2(0.44, 0.14);

const float BLINK_PERIOD_LEAST = 2.2;
const float BLINK_PERIOD_MOST = 6.5;
const float BLINK_SECONDS = 0.26;

const float LIDS_RELAXED = 1.04;
const float LIDS_STARING = 1.2;
const float PUPIL_RELAXED = 0.44;
const float PUPIL_STARING = 0.34;

float hash(float value)
{
    return fract(sin(value * 91.3458) * 47453.5453);
}

float mixed(float a, float b)
{
    return hash(a * 37.17 + b * 113.5);
}

vec2 dart(float clock, float seed)
{
    float beat = floor(clock);
    float blend = smoothstep(0.0, WANDER_DART_SHARE, fract(clock));
    vec2 before = vec2(mixed(beat - 1.0, seed), mixed(beat - 1.0, seed + 3.1));
    vec2 after = vec2(mixed(beat, seed), mixed(beat, seed + 3.1));
    return mix(before, after, blend) * 2.0 - 1.0;
}

void main() {
    vUv = Corner.xy;

    vec3 center = (StarView * vec4(Center, 1.0)).xyz;
    vec3 normal = normalize(center);
    vec3 up = (SkyView * vec4(0.0, 1.0, 0.0, 0.0)).xyz;
    float elevation = clamp(dot(normal, up), 0.0, 1.0);

    float grow = smoothstep(Seed.x * OPEN_SPREAD, Seed.x * OPEN_SPREAD + OPEN_SPAN, CurseStateA.x);
    float leaving = clamp((CurseStateA.z - Seed.y * CLOSE_SPREAD) / (1.0 - CLOSE_SPREAD), 0.0, 1.0);
    grow *= 1.0 - leaving;

    float stareStart = mix(Seed.y, elevation, STARE_WAVE) * (1.0 - STARE_SPAN);
    float stare = clamp((CurseStateA.y - stareStart) / STARE_SPAN, 0.0, 1.0);
    stare = stare * stare * (3.0 - 2.0 * stare);

    float seedA = mixed(Seed.x, Seed.w);
    float seedB = mixed(Seed.y, Seed.z);
    float wanderRate = mix(WANDER_RATE_LEAST, WANDER_RATE_MOST, seedA);
    vec2 iris = dart(CurseTime.x * wanderRate + seedB * 31.0, seedA * 57.0) * WANDER_REACH * (1.0 - stare);

    float blinkPeriod = mix(BLINK_PERIOD_LEAST, BLINK_PERIOD_MOST, seedB);
    float blinkAt = fract(CurseTime.x / blinkPeriod + seedA) * blinkPeriod;
    float blink = clamp(1.0 - abs(blinkAt - BLINK_SECONDS * 0.5) / (BLINK_SECONDS * 0.5), 0.0, 1.0) * (1.0 - stare);

    float lids = smoothstep(LIDS_PART_FROM, LIDS_PART_TO, grow) * mix(LIDS_RELAXED, LIDS_STARING, stare) * (1.0 - blink);

    vLook = vec4(iris, lids, stare);
    vEye = vec4(1.0 - smoothstep(0.0, STAR_GONE_AT, grow), mix(PUPIL_RELAXED, PUPIL_STARING, stare), seedA, seedB);

    if (grow <= 0.0)
    {
        // The vanilla star itself, without anything rounded differently
        gl_Position = ProjMat * StarView * vec4(Position, 1.0);
        return;
    }

    vec3 right = cross(normal, up);
    float rightLength = length(right);
    right = rightLength > 0.02 ? right / rightLength : normalize(cross(normal, (SkyView * vec4(0.0, 0.0, 1.0, 0.0)).xyz));
    vec3 eyeUp = cross(right, normal);

    // The star is a square at an angle of its own, which turns upright as it grows
    vec3 starCorner = (StarView * vec4(Position - Center, 0.0)).xyz;
    vec2 flatCorner = vec2(dot(starCorner, right), dot(starCorner, eyeUp));
    float starSize = length(flatCorner) * 0.7071;
    float angle = atan(flatCorner.y, flatCorner.x) - atan(Corner.y, Corner.x);
    angle = mod(angle + 3.14159265, 6.28318531) - 3.14159265;

    float width = mix(WIDTH_LEAST, WIDTH_MOST, pow(Seed.z, WIDTH_BIAS)) * (1.0 + stare * STARE_GROWTH);
    float tilt = (Seed.w - 0.5) * 2.0 * TILT_MOST;
    float turned = mix(angle, tilt, smoothstep(0.0, 0.5, grow));
    float scaled = grow * grow * (3.0 - 2.0 * grow);
    vec2 size = mix(vec2(starSize), vec2(width, width * ASPECT), scaled);

    vec2 local = Corner.xy * size;
    float c = cos(turned);
    float s = sin(turned);
    local = vec2(local.x * c - local.y * s, local.x * s + local.y * c);

    gl_Position = ProjMat * vec4(center + right * local.x + eyeUp * local.y, 1.0);
}
