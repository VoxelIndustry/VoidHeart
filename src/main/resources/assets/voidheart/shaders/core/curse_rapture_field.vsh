#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <voidheart:curse_sky.glsl>

// Every star turns into an eye at its own moment, and back, or if there is no room for one fades away for that time.
// CurseStateA.x runs from 0 to 1 and beyond while they open, y from 0 to 1 while they come to stare, z from 0 to 1 while they close.

layout(location = 0) in vec3 Position;
layout(location = 1) in vec3 Center;
layout(location = 2) in vec4 Corner;
layout(location = 3) in vec4 Seed;
layout(location = 4) in float Width;
layout(location = 5) in vec4 Shape;
layout(location = 6) in vec4 Sclera;
layout(location = 7) in vec4 Iris;
layout(location = 8) in vec4 Lid;

layout(location = 0) out vec2 vTexel;
layout(location = 1) flat out vec4 vLook;
layout(location = 2) flat out vec4 vStar;
layout(location = 3) flat out vec4 vOpen;
layout(location = 4) flat out vec4 vBend;
layout(location = 5) flat out vec4 vPupil;
layout(location = 6) flat out vec4 vLids;
layout(location = 7) flat out vec4 vSclera;
layout(location = 8) flat out vec4 vIris;
layout(location = 9) flat out vec4 vLid;
layout(location = 10) flat out vec2 vReach;

// Blocks at the dome's radius. The same in the fragment shader and in RaptureFieldLayout
const float TEXEL = 0.5;
const float WIDTH_LEAST = 1.0;
const float WIDTH_MOST = 8.0;
// The same as in RaptureFieldMesh
const float TILT_RANGE = 2.4;

// Texels past the eye the quad reaches, for a texel with its center inside to be there whole
const float QUAD_PAST_EYE = 0.6;
// Below this half width an eye is the plain pattern of the fragment shader and nothing else. The same as SIMPLE_BELOW in RaptureFieldLayout
const float SPECK_BELOW = 1.5;
// An eye without lids has nothing to close: it shrinks away, losing its rim once it gets below this many texels of half width
const float LIDLESS_RIM_FROM = 3.0;

// While not staring, as a share of its widest size. The smallest are only a few texels to begin with, and keep them
const float RELAXED_WIDTH = 0.86;
const float RELAXED_FROM = 0.9;
const float RELAXED_TO = 3.0;

// Shares of the opening window: when the last small eye starts, how long one takes, and the same for the largest, which open late and slowly
const float OPEN_SPREAD = 0.56;
const float OPEN_SPAN = 0.44;
const float OPEN_LATE_FROM = 0.42;
const float OPEN_LATE_SPREAD = 0.24;
const float OPEN_SPAN_SLOW = 0.6;
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
// The largest move their eyes less often
const float WANDER_RATE_LARGEST = 0.6;
const float WANDER_DART_SHARE = 0.16;

const float BLINK_PERIOD_LEAST = 2.2;
const float BLINK_PERIOD_MOST = 6.5;
const float BLINK_SECONDS = 0.26;

// How far openings and irises stray from their family's value, and pupils get smaller as they stare
const float OPENING_VARIES = 0.12;
const float IRIS_VARIES = 0.08;
const float PUPIL_VARIES = 0.2;
const float PUPIL_STARING = 0.8;
// How many texels a pupil reaches from its middle at least, or with its corners cut it would fall between the texel centers around the middle.
// Slits and bars take a single row along their thin side instead
const float PUPIL_LEAST = 0.85;
const float PUPIL_THIN_LEAST = 0.5;
// An opening reaches at least this many texels from the middle for a row of them to be there at all
const float OPENING_LEAST = 0.9;

// Small eyes have round openings in place of their family's sharp corners, as they are only a few texels long
const float SHARPNESS_SMALL = 0.25;
const float SHARP_FROM = 0.9;
const float SHARP_TO = 1.7;

// Lids get as thick as their family has them at WIDTH_MOST, starting out at one texel from this width
const float LID_FROM = 1.4;

// Per family, in the order of RaptureFieldLayout.Family: almond, round, narrow, heavy, goat, slit, wrong.
// All lengths in half widths, unless they say texels. Openings are measured with the lids at 1
const float OPEN_TOP[7] = float[7](0.46, 0.84, 0.21, 0.27, 0.52, 0.54, 0.60);
const float OPEN_BOTTOM[7] = float[7](0.34, 0.84, 0.17, 0.44, 0.44, 0.42, 0.50);
// How far the opening reaches sideways at most: a round one stays round, the others go as far as their lids let them
const float OPEN_SIDE[7] = float[7](1.00, 0.84, 1.00, 1.00, 0.94, 1.00, 1.00);
const float SHARP_TOP[7] = float[7](0.72, 0.50, 0.75, 0.60, 0.64, 0.62, 0.55);
const float SHARP_BOTTOM[7] = float[7](0.90, 0.50, 0.85, 0.72, 0.74, 0.90, 0.55);
// Outer corner up, then both corners falling
const float SLANT[7] = float[7](0.05, 0.00, 0.44, -0.04, 0.05, 0.24, 0.00);
const float DROOP[7] = float[7](0.00, 0.00, 0.00, 0.26, 0.03, 0.00, 0.00);
const float LID_TOP[7] = float[7](3.0, 1.0, 4.0, 7.0, 3.0, 2.0, 2.0);
const float LID_BOTTOM[7] = float[7](1.0, 1.0, 3.0, 4.0, 2.0, 1.0, 1.0);
const float LIDS_STARING[7] = float[7](1.20, 1.00, 1.30, 1.16, 1.10, 1.12, 1.10);
// Zero for those with no lids to close
const float BLINKS[7] = float[7](1.0, 0.0, 0.7, 1.0, 1.0, 1.0, 0.0);
const float IRIS_RADIUS[7] = float[7](0.46, 0.60, 0.50, 0.46, 0.66, 0.60, 0.56);
const float PUPIL_WIDE[7] = float[7](0.42, 0.30, 0.12, 0.42, 0.80, 0.11, 0.30);
const float PUPIL_TALL[7] = float[7](0.42, 0.30, 0.92, 0.42, 0.22, 0.94, 0.30);
const float PUPIL_TWINS[7] = float[7](0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.40);
const vec2 WANDER_REACH[7] = vec2[7](vec2(0.44, 0.14), vec2(0.30, 0.22), vec2(0.34, 0.05), vec2(0.40, 0.05), vec2(0.24, 0.05), vec2(0.30, 0.10), vec2(0.34, 0.20));

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
    vTexel = vec2(0.0);
    vLook = vec4(0.0);
    vOpen = vec4(0.0);
    vBend = vec4(0.0);
    vPupil = vec4(0.0);
    vLids = vec4(0.0);
    vReach = vec2(1.0);
    vSclera = Sclera;
    vIris = Iris;
    vLid = vec4(Lid.rgb, Width < SPECK_BELOW ? 1.0 : 0.0);

    vec3 center = (StarView * vec4(Center, 1.0)).xyz;
    vec3 normal = normalize(center);
    vec3 up = (SkyView * vec4(0.0, 1.0, 0.0, 0.0)).xyz;
    float elevation = clamp(dot(normal, up), 0.0, 1.0);

    float sizeShare = clamp((Width - WIDTH_LEAST) / (WIDTH_MOST - WIDTH_LEAST), 0.0, 1.0);
    float openFrom = mix(Seed.x * OPEN_SPREAD, OPEN_LATE_FROM + Seed.x * OPEN_LATE_SPREAD, sizeShare);
    float grow = smoothstep(openFrom, openFrom + mix(OPEN_SPAN, OPEN_SPAN_SLOW, sizeShare), CurseStateA.x);
    float leaving = clamp((CurseStateA.z - Seed.y * CLOSE_SPREAD) / (1.0 - CLOSE_SPREAD), 0.0, 1.0);
    grow *= 1.0 - leaving;

    if (Width <= 0.0 || grow <= 0.0)
    {
        // The vanilla star itself, without anything rounded differently
        vStar = vec4(1.0 - grow, 0.0, 0.0, -1.0);
        gl_Position = ProjMat * StarView * vec4(Position, 1.0);
        return;
    }

    int family = int(Shape.x * 255.0 + 0.5);

    float stareStart = mix(Seed.y, elevation, STARE_WAVE) * (1.0 - STARE_SPAN);
    float stare = clamp((CurseStateA.y - stareStart) / STARE_SPAN, 0.0, 1.0);
    stare = stare * stare * (3.0 - 2.0 * stare);

    float seedA = mixed(Seed.x, Seed.w);
    float seedB = mixed(Seed.y, Seed.z);
    float wanderRate = mix(WANDER_RATE_LEAST, WANDER_RATE_MOST, seedA) * mix(1.0, WANDER_RATE_LARGEST, sizeShare);
    vec2 iris = dart(CurseTime.x * wanderRate + seedB * 31.0, seedA * 57.0) * WANDER_REACH[family] * (1.0 - stare);

    float blinkPeriod = mix(BLINK_PERIOD_LEAST, BLINK_PERIOD_MOST, seedB);
    float blinkAt = fract(CurseTime.x / blinkPeriod + seedA) * blinkPeriod;
    float blink = clamp(1.0 - abs(blinkAt - BLINK_SECONDS * 0.5) / (BLINK_SECONDS * 0.5), 0.0, 1.0) * (1.0 - stare) * BLINKS[family];

    bool lidless = BLINKS[family] <= 0.0;
    float lids = (lidless ? 1.0 : smoothstep(LIDS_PART_FROM, LIDS_PART_TO, grow)) * mix(1.0, LIDS_STARING[family], stare) * (1.0 - blink);
    vLook = vec4(iris, lids, stare);

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

    float relaxed = mix(1.0, RELAXED_WIDTH, smoothstep(RELAXED_FROM, RELAXED_TO, Width));
    float widthNow = Width * mix(relaxed, 1.0, stare);
    float turned = mix(angle, Corner.z * TILT_RANGE, smoothstep(0.0, 0.5, grow));
    float scaled = grow * grow * (3.0 - 2.0 * grow);
    float eyeSize = widthNow * scaled;
    float size = mix(starSize, widthNow + TEXEL * QUAD_PAST_EYE, scaled);

    float widthTexels = max(eyeSize / TEXEL, 0.001);
    vTexel = Corner.xy * size / TEXEL;
    vStar = vec4(1.0 - smoothstep(0.0, STAR_GONE_AT, grow), starSize / TEXEL, widthTexels, float(family));

    float opening = mix(1.0 - OPENING_VARIES, 1.0 + OPENING_VARIES, Shape.y);
    float least = OPENING_LEAST / widthTexels;
    float sharp = smoothstep(SHARP_FROM, SHARP_TO, Width);
    vOpen = vec4(max(OPEN_TOP[family] * opening, least), max(OPEN_BOTTOM[family] * opening, least),
            mix(SHARPNESS_SMALL, SHARP_TOP[family], sharp), mix(SHARPNESS_SMALL, SHARP_BOTTOM[family], sharp));
    vBend = vec4(SLANT[family] * sharp, DROOP[family] * sharp, Corner.w, Lid.a * 2.0 - 1.0);

    float radius = IRIS_RADIUS[family] * mix(1.0 - IRIS_VARIES, 1.0 + IRIS_VARIES, Shape.w);
    float pupilScale = mix(1.0 - PUPIL_VARIES, 1.0 + PUPIL_VARIES, Shape.z) * mix(1.0, PUPIL_STARING, stare);
    float pupilWide = PUPIL_WIDE[family] * radius * pupilScale;
    float pupilTall = PUPIL_TALL[family] * radius * pupilScale;
    vPupil = vec4(radius, max(pupilWide, (pupilWide < pupilTall * 0.5 ? PUPIL_THIN_LEAST : PUPIL_LEAST) / widthTexels),
            max(pupilTall, (pupilTall < pupilWide * 0.65 ? PUPIL_THIN_LEAST : PUPIL_LEAST) / widthTexels), PUPIL_TWINS[family] * radius);

    float lidGrowth = clamp((Width - LID_FROM) / (WIDTH_MOST - LID_FROM), 0.0, 1.0);
    bool rimless = lidless && widthTexels < LIDLESS_RIM_FROM;
    vLids = vec4(rimless ? 0.0 : max(floor(LID_TOP[family] * lidGrowth + 0.5), 1.0), rimless ? 0.0 : max(floor(LID_BOTTOM[family] * lidGrowth + 0.5), 1.0), seedA, seedB);
    float aside = rimless ? 0.0 : 1.0 / widthTexels;
    vReach = vec2(min(1.0 - aside, OPEN_SIDE[family]), min(1.0 - aside, OPEN_SIDE[family]) + aside);

    vec2 local = Corner.xy * size;
    float c = cos(turned);
    float s = sin(turned);
    local = vec2(local.x * c - local.y * s, local.x * s + local.y * c);

    gl_Position = ProjMat * vec4(center + right * local.x + eyeUp * local.y, 1.0);
}
