#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <voidheart:curse_sky.glsl>
#include <voidheart:curse_omen_wave.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec3 Center;
layout(location = 2) in vec4 Corner;
layout(location = 3) in vec4 Seed;

// (-1, -1) to (1, 1) over the vanilla star
layout(location = 0) out vec2 corner;
// How far this star has turned red
layout(location = 1) out float turned;
layout(location = 2) out float glow;

// Share of the whole turn that one star takes, and how much of its moment comes from where it sits rather than from chance
const float TURN_SPAN = 0.3;
const float TURN_PATCHES = 0.55;
const float TURN_PATCH_SCALE = 2.2;

// In blocks at the radius of the dome, and radians per second
const float WOBBLE_MIN = 0.3;
const float WOBBLE_MAX = 0.6;
const float WOBBLE_RATE_MIN = 1.1;
const float WOBBLE_RATE_MAX = 2.8;
const float WOBBLE_OVERTONE = 0.45;

const float GLOW_MIN = 0.62;
const float GLOW_MAX = 1.0;
// How much brighter a star gets while a wave passes through it
const float GLOW_ON_WAVE = 1.6;

float turnAt(float progress, float moment) {
    float start = moment * (1.0 - TURN_SPAN);
    return smoothstep(start, start + TURN_SPAN, progress);
}

void main() {
    vec3 up = normalize(Center);
    // The stars turn with the time of day, the waves stay with the player
    vec3 skyDir = transpose(mat3(SkyView)) * (mat3(StarView) * up);
    float seed = CurseStateB.x * 61.0;

    float patchIn = clamp(curseOmenNoise(skyDir * TURN_PATCH_SCALE + vec3(seed, 0.0, 7.0)) * 1.6 - 0.3, 0.0, 1.0);
    float patchOut = clamp(curseOmenNoise(skyDir * TURN_PATCH_SCALE + vec3(0.0, seed, 13.0)) * 1.6 - 0.3, 0.0, 1.0);
    float turnIn = turnAt(clamp(CurseStateA.x, 0.0, 1.0), mix(Seed.x, patchIn, TURN_PATCHES));
    float turnOut = turnAt(clamp(CurseStateA.x - 1.0, 0.0, 1.0), mix(Seed.y, patchOut, TURN_PATCHES));
    turned = turnIn * (1.0 - turnOut);

    if (turned <= 0.0) {
        // Exactly the vanilla star
        corner = Corner.xy;
        glow = 0.0;
        gl_Position = ProjMat * StarView * vec4(Position, 1.0);
        return;
    }

    float rate = mix(WOBBLE_RATE_MIN, WOBBLE_RATE_MAX, Seed.w);
    float seconds = CurseTime.x;
    float phaseA = Seed.y * OMEN_TAU * 5.0;
    float phaseB = Seed.z * OMEN_TAU * 7.0;
    vec2 drift = vec2(sin(seconds * rate + phaseA) + WOBBLE_OVERTONE * sin(seconds * rate * 2.3 + phaseB),
                      sin(seconds * rate * 1.17 + phaseB) + WOBBLE_OVERTONE * sin(seconds * rate * 1.9 + phaseA * 1.3));
    drift /= 1.0 + WOBBLE_OVERTONE;
    float reach = mix(WOBBLE_MIN, WOBBLE_MAX, fract(Seed.x * 17.0 + Seed.w * 31.0)) * CurseStateA.y * turned;

    vec3 east = normalize(cross(up, abs(up.y) < 0.99 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)));
    vec3 north = cross(east, up);
    vec3 moved = Center + (Position - Center) * OMEN_STAR_BLOOM + (east * drift.x + north * drift.y) * reach;

    float brightness = mix(GLOW_MIN, GLOW_MAX, Seed.z);
    glow = brightness * (1.0 + GLOW_ON_WAVE * curseOmenWave(skyDir) * CurseStateA.z);
    corner = Corner.xy * OMEN_STAR_BLOOM;
    gl_Position = ProjMat * StarView * vec4(moved, 1.0);
}
