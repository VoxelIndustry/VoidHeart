#ifndef VOIDHEART_CURSE_OMEN_WAVE_GLSL
#define VOIDHEART_CURSE_OMEN_WAVE_GLSL

#include <voidheart:curse_sky.glsl>

// CurseStateA.x: how far the stars have turned, 0 to 1 toward red then 1 to 2 back again, y: how much they wobble, z: how strong the waves are, w: angle of the moon
// CurseStateB.x: a random value for this curse, y: seconds since the waves began, z and w: azimuth and height of the first source

const float OMEN_TAU = 6.2831853;

// How much wider a turned star's quad is than the vanilla one, leaving room for its soft edge and halo
const float OMEN_STAR_BLOOM = 3.4;

// Each source starts at its own moment and sends out a short train of rings. When that has died out, it starts over somewhere else
const int WAVE_SOURCES = 6;
const float WAVE_BIRTH_GAP = 0.5;
const float WAVE_BIRTH_JITTER = 0.6;
const float WAVE_LIFE_MIN = 5.5;
const float WAVE_LIFE_MAX = 8.5;
const float WAVE_LIFE_FADE_FROM = 0.6;
// As the height of a point on the unit sphere, so that sources stay above the horizon
const float WAVE_SOURCE_LOWEST = 0.3;
const float WAVE_SOURCE_HIGHEST = 0.97;
// Distances are chords of the unit sphere, so 1.41 is a quarter of the way around it
const float WAVE_SPEED_MIN = 0.22;
const float WAVE_SPEED_MAX = 0.36;
const float WAVE_LENGTH_MIN = 0.16;
const float WAVE_LENGTH_MAX = 0.28;
const float WAVE_FRONT_SOFTNESS = 0.11;
// How quickly a train dies out behind its front, and with the distance traveled
const float WAVE_TRAIN_DECAY = 1.6;
const float WAVE_TRAVEL_DECAY = 0.35;
const float WAVE_WARP_WIDE = 0.26;
const float WAVE_WARP_FINE = 0.09;
const float WAVE_WARP_WIDE_SCALE = 1.9;
const float WAVE_WARP_FINE_SCALE = 5.1;
const float WAVE_WARP_WIDE_DRIFT = 0.11;
const float WAVE_WARP_FINE_DRIFT = 0.19;
const float WAVE_PATCH_SCALE = 1.7;
const float WAVE_PATCH_DRIFT = 0.14;
const float WAVE_PATCH_LOW = 0.24;
const float WAVE_PATCH_HIGH = 0.66;
const float WAVE_PATCH_FLOOR = 0.4;
// Narrow crests with shallow troughs between them: where rings cross, a crest stands out or a trough eats into it
const float WAVE_TROUGH = 0.12;
const float WAVE_GAIN = 2.6;

float curseOmenHash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

float curseOmenNoise(vec3 p) {
    vec3 cell = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(curseOmenHash(cell), curseOmenHash(cell + vec3(1, 0, 0)), f.x),
                   mix(curseOmenHash(cell + vec3(0, 1, 0)), curseOmenHash(cell + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(curseOmenHash(cell + vec3(0, 0, 1)), curseOmenHash(cell + vec3(1, 0, 1)), f.x),
                   mix(curseOmenHash(cell + vec3(0, 1, 1)), curseOmenHash(cell + vec3(1, 1, 1)), f.x), f.y), f.z);
}

// In 0..1 for a direction of the still sky, before the overall strength of the waves
float curseOmenWave(vec3 dir) {
    float seconds = CurseStateB.y;
    float seed = CurseStateB.x * 61.0;

    float warpWide = curseOmenNoise(dir * WAVE_WARP_WIDE_SCALE + vec3(seed, seconds * WAVE_WARP_WIDE_DRIFT, 0.0)) - 0.5;
    float warpFine = curseOmenNoise(dir * WAVE_WARP_FINE_SCALE + vec3(seconds * WAVE_WARP_FINE_DRIFT, seed, 2.0)) - 0.5;
    float patches = smoothstep(WAVE_PATCH_LOW, WAVE_PATCH_HIGH, curseOmenNoise(dir * WAVE_PATCH_SCALE + vec3(3.0, seed, seconds * WAVE_PATCH_DRIFT)));

    float sum = 0.0;
    for (int i = 0; i < WAVE_SOURCES; i++) {
        float index = float(i) + 1.0;
        float sinceBirth = seconds - (float(i) * WAVE_BIRTH_GAP + curseOmenHash(vec3(seed, index, 1.0)) * WAVE_BIRTH_JITTER);
        if (sinceBirth <= 0.0) {
            continue;
        }
        float life = mix(WAVE_LIFE_MIN, WAVE_LIFE_MAX, curseOmenHash(vec3(seed, index, 7.0)));
        float rebirths = floor(sinceBirth / life);
        float age = sinceBirth - rebirths * life;
        index += rebirths * 11.0;

        bool first = i == 0 && rebirths == 0.0;
        float height = first ? CurseStateB.w : mix(WAVE_SOURCE_LOWEST, WAVE_SOURCE_HIGHEST, curseOmenHash(vec3(seed, index, 2.0)));
        float azimuth = first ? CurseStateB.z : curseOmenHash(vec3(seed, index, 3.0)) * OMEN_TAU;
        float across = sqrt(1.0 - height * height);
        vec3 source = vec3(cos(azimuth) * across, height, sin(azimuth) * across);

        float front = age * mix(WAVE_SPEED_MIN, WAVE_SPEED_MAX, curseOmenHash(vec3(seed, index, 4.0)));
        float wavelength = mix(WAVE_LENGTH_MIN, WAVE_LENGTH_MAX, curseOmenHash(vec3(seed, index, 5.0)));
        float warpTurn = curseOmenHash(vec3(seed, index, 6.0)) * 2.0 - 1.0;

        float away = length(dir - source) + warpWide * WAVE_WARP_WIDE * warpTurn + warpFine * WAVE_WARP_FINE;
        float behind = front - away;
        if (behind <= 0.0) {
            continue;
        }
        float strength = smoothstep(0.0, WAVE_FRONT_SOFTNESS, behind) * exp(-behind * WAVE_TRAIN_DECAY - max(away, 0.0) * WAVE_TRAVEL_DECAY)
                * (1.0 - smoothstep(life * WAVE_LIFE_FADE_FROM, life, age));
        float ring = sin(behind / wavelength * OMEN_TAU) * 0.5 + 0.5;
        ring = ring * ring * ring;
        sum += (ring * ring - WAVE_TROUGH) * strength;
    }

    float crest = max(sum, 0.0) * mix(WAVE_PATCH_FLOOR, 1.0, patches);
    return 1.0 - exp(-crest * WAVE_GAIN);
}

#endif
