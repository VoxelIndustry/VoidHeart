#ifndef VOIDHEART_CURSE_OMEN_WAVE_GLSL
#define VOIDHEART_CURSE_OMEN_WAVE_GLSL

#include <voidheart:curse_sky.glsl>

// CurseStateA.x: how far the stars have turned, 0 to 1 toward red then 1 to 2 back again, y: how much they wobble, z: how strong the waves are, w: angle of the moon
// CurseStateB.x: a random value for this curse, y: seconds since the waves began, z and w: azimuth and height of the first source

const float OMEN_TAU = 6.2831853;

// How much wider a turned star's quad is than the vanilla one, leaving room for its soft edge and halo
const float OMEN_STAR_BLOOM = 3.4;

// Each source starts at its own moment and sends out a short train of rings. When that has died out and the sky has rested, it starts over somewhere else.
// Few enough of them that each set of rings stays recognizable, spread around the sky so that they meet rather than pile up
const int WAVE_SOURCES = 4;
const float WAVE_BIRTH_GAP = 1.6;
const float WAVE_BIRTH_JITTER = 0.5;
const float WAVE_LIFE_MIN = 6.0;
const float WAVE_LIFE_MAX = 8.0;
const float WAVE_LIFE_FADE_FROM = 0.45;
const float WAVE_REST_MIN = 0.6;
const float WAVE_REST_MAX = 1.6;
const float WAVE_AZIMUTH_JITTER = 1.0;
const float WAVE_REBIRTH_TURN = 2.4;
// As the height of a point on the unit sphere. Sources high up send arcs along the horizon instead of curtains standing on it
const float WAVE_SOURCE_LOWEST = 0.55;
const float WAVE_SOURCE_HIGHEST = 0.95;
// Distances are chords of the unit sphere, so 1.41 is a quarter of the way around it
const float WAVE_SPEED_MIN = 0.22;
const float WAVE_SPEED_MAX = 0.36;
const float WAVE_LENGTH_MIN = 0.2;
const float WAVE_LENGTH_MAX = 0.3;
const float WAVE_FRONT_SOFTNESS = 0.11;
// How quickly a train dies out behind its front, and with the distance traveled
const float WAVE_TRAIN_DECAY = 1.4;
const float WAVE_TRAVEL_DECAY = 0.5;
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
const float WAVE_GAIN = 2.8;

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

// x and y: how far this direction pushes rings off a clean circle, z: how strongly fronts show here. Varies slowly over the sky
vec3 curseOmenFields(vec3 dir) {
    float seconds = CurseStateB.y;
    float seed = CurseStateB.x * 61.0;
    float warpWide = curseOmenNoise(dir * WAVE_WARP_WIDE_SCALE + vec3(seed, seconds * WAVE_WARP_WIDE_DRIFT, 0.0)) - 0.5;
    float warpFine = curseOmenNoise(dir * WAVE_WARP_FINE_SCALE + vec3(seconds * WAVE_WARP_FINE_DRIFT, seed, 2.0)) - 0.5;
    float patches = smoothstep(WAVE_PATCH_LOW, WAVE_PATCH_HIGH, curseOmenNoise(dir * WAVE_PATCH_SCALE + vec3(3.0, seed, seconds * WAVE_PATCH_DRIFT)));
    return vec3(warpWide * WAVE_WARP_WIDE, warpFine * WAVE_WARP_FINE, mix(WAVE_PATCH_FLOOR, 1.0, patches));
}

// wave: where each source is, then how far its front has come, negative when it is at rest. shape: wavelength, which way the warp pushes it, and how alive it still is
void curseOmenSources(out vec4 wave[WAVE_SOURCES], out vec4 shape[WAVE_SOURCES]) {
    float seconds = CurseStateB.y;
    float seed = CurseStateB.x * 61.0;
    for (int i = 0; i < WAVE_SOURCES; i++) {
        wave[i] = vec4(0.0, 1.0, 0.0, -1.0);
        shape[i] = vec4(1.0, 0.0, 0.0, 0.0);

        float index = float(i) + 1.0;
        float sinceBirth = seconds - (float(i) * WAVE_BIRTH_GAP + curseOmenHash(vec3(seed, index, 1.0)) * WAVE_BIRTH_JITTER);
        if (sinceBirth <= 0.0) {
            continue;
        }
        float life = mix(WAVE_LIFE_MIN, WAVE_LIFE_MAX, curseOmenHash(vec3(seed, index, 7.0)));
        float cycle = life + mix(WAVE_REST_MIN, WAVE_REST_MAX, curseOmenHash(vec3(seed, index, 8.0)));
        float rebirths = floor(sinceBirth / cycle);
        float age = sinceBirth - rebirths * cycle;
        if (age >= life) {
            continue;
        }
        index += rebirths * 11.0;

        bool first = i == 0 && rebirths == 0.0;
        float height = first ? CurseStateB.w : mix(WAVE_SOURCE_LOWEST, WAVE_SOURCE_HIGHEST, curseOmenHash(vec3(seed, index, 2.0)));
        float azimuth = CurseStateB.z;
        if (!first) {
            azimuth += float(i) * OMEN_TAU / float(WAVE_SOURCES) + rebirths * WAVE_REBIRTH_TURN + (curseOmenHash(vec3(seed, index, 3.0)) - 0.5) * WAVE_AZIMUTH_JITTER;
        }
        float across = sqrt(1.0 - height * height);
        float front = age * mix(WAVE_SPEED_MIN, WAVE_SPEED_MAX, curseOmenHash(vec3(seed, index, 4.0)));
        wave[i] = vec4(cos(azimuth) * across, height, sin(azimuth) * across, front);
        shape[i] = vec4(mix(WAVE_LENGTH_MIN, WAVE_LENGTH_MAX, curseOmenHash(vec3(seed, index, 5.0))),
                        curseOmenHash(vec3(seed, index, 6.0)) * 2.0 - 1.0,
                        1.0 - smoothstep(life * WAVE_LIFE_FADE_FROM, life, age),
                        0.0);
    }
}

// In 0..1, before the overall strength of the waves. This part alone runs per pixel, the rest of the wave varies too slowly to need it
float curseOmenRings(vec3 dir, vec3 fields, vec4 wave[WAVE_SOURCES], vec4 shape[WAVE_SOURCES]) {
    float sum = 0.0;
    for (int i = 0; i < WAVE_SOURCES; i++) {
        if (wave[i].w <= 0.0) {
            continue;
        }
        float away = length(dir - wave[i].xyz) + fields.x * shape[i].y + fields.y;
        float behind = wave[i].w - away;
        if (behind <= 0.0) {
            continue;
        }
        float strength = smoothstep(0.0, WAVE_FRONT_SOFTNESS, behind) * exp(-behind * WAVE_TRAIN_DECAY - max(away, 0.0) * WAVE_TRAVEL_DECAY) * shape[i].z;
        float ring = sin(behind / shape[i].x * OMEN_TAU) * 0.5 + 0.5;
        ring = ring * ring * ring;
        sum += (ring * ring - WAVE_TROUGH) * strength;
    }
    return 1.0 - exp(-max(sum, 0.0) * fields.z * WAVE_GAIN);
}

float curseOmenWave(vec3 dir) {
    vec4 wave[WAVE_SOURCES];
    vec4 shape[WAVE_SOURCES];
    curseOmenSources(wave, shape);
    return curseOmenRings(dir, curseOmenFields(dir), wave, shape);
}

#endif
