#version 330
#extension GL_ARB_separate_shader_objects : require

#include <voidheart:curse_sky.glsl>

// Texels from the center of the eye. One texel is the same size on every eye in the sky, so that a small eye is simple and only a large one has any detail
layout(location = 0) in vec2 vTexel;
// x, y: where the iris looks. z: how far the lids part. w: how much it stares
layout(location = 1) flat in vec4 vLook;
// x: how much of the plain star is left. y: its half size in texels. z: half width of the eye in texels. w: family, or below zero for a plain star
layout(location = 2) flat in vec4 vStar;
// How far the opening reaches up and down in half widths, and how sharp its corners are at each
layout(location = 3) flat in vec4 vOpen;
// x: slant. y: droop. z: lean of the upper lid. w: 1 or -1 for which way the eye faces
layout(location = 4) flat in vec4 vBend;
// x: iris radius. y, z: pupil half width and height. w: how far each of two pupils sits from the middle
layout(location = 5) flat in vec4 vPupil;
// x, y: thickness of the upper and lower lid in texels. z, w: random values for this eye
layout(location = 6) flat in vec4 vLids;
// a: how bloodshot
layout(location = 7) flat in vec4 vSclera;
// a: how much the pupil stands out
layout(location = 8) flat in vec4 vIris;
// a: whether this eye is only the plain pattern for the smallest
layout(location = 9) flat in vec4 vLid;
// x: how far the opening reaches sideways. y: how far all of the eye does, lids included
layout(location = 10) flat in vec2 vReach;

layout(location = 0) out vec4 fragColor;

const int ROUND = 1;
const int NARROW = 2;
const int HEAVY = 3;
const int GOAT = 4;
const int SLIT = 5;
const int WRONG = 6;

// What needs how many texels of half width before it is drawn at all
const float LASH_FROM = 3.2;
const float SHADE_FROM = 4.0;
const float CORNERS_FROM = 6.0;
const float RING_FROM = 4.0;
const float BANDS_FROM = 3.5;
const float GLINT_FROM = 3.6;
const float GLINT_LARGE_FROM = 9.0;
const float VEINS_FROM = 8.0;
const float FIBERS_FROM = 9.0;
const float FLECKS_FROM = 10.0;
const float BULGE_FROM = 4.0;
const float DIRT_FROM = 7.0;
const float SCALES_FROM = 2.0;

// An iris this few texels wide can't have a pupil of its own, so all of it darkens
const float IRIS_SOLID_BELOW = 1.6;
const float IRIS_SOLID_DARK = 0.78;

const float LASH = 0.32;
const float LID_NEAR = 1.18;
const float LID_FAR = 0.78;
const float LID_LOWER = 0.82;
const float CREASE = 0.5;
const float RIM = 0.7;
// However blunt the opening's corners, those of the lids around it are at least this round
const float OUTER_SHARPNESS_LEAST = 0.5;
const float SCALE_DARK = 0.62;
const float SCALE_LIGHT = 1.28;
const float FUR_DARK = 0.68;

const float SHADE_UNDER_LID = 0.58;
const float SHADE_NEXT = 0.82;
const float CORNER_NEAR = 0.88;
const float CORNER_FAR = 0.68;
const float DIRT = 0.92;
const float BULGE_NEAR = 0.84;
const float BULGE_FAR = 0.6;
const vec3 VEIN = vec3(0.56, 0.13, 0.17);
const float VEIN_SLICES = 22.0;
const float VEIN_LIMIT = 0.5;
const float VEIN_RUN = 3.0;
const float VEIN_THICKNESS = 0.55;

const float IRIS_RING = 0.52;
const float IRIS_OUTER = 0.8;
const float IRIS_COLLAR = 1.24;
const float IRIS_CRESCENT = 1.1;
const float IRIS_UNDER_LID = 0.62;
const float FIBERS = 34.0;
const float FIBER_DEPTH = 0.32;
const float FLECK_SHARE = 0.03;
const float FLECK_DARK = 0.68;
const float FLECK_LIGHT = 1.25;
const vec3 GLINT = vec3(0.86, 0.84, 0.78);
const float GLINT_STRENGTH = 0.8;
const float GLINT_AT = 0.42;

const vec3 PUPIL = vec3(0.012, 0.01, 0.02);
const float PUPIL_CORNERS = 1.25;
// Half size in texels under which a slit or a bar takes one row, between the least it can be in the vertex shader and the least of a round one
const float PUPIL_TWO_ROWS = 0.7;
// Against the night a black pupil in a small iris looks like a hole through it
const float PUPIL_SOFT_BELOW = 4.0;
const float PUPIL_SOFT = 0.72;
// Of the second pupil of a wrong eye, set off diagonally so that the two don't look like a little face
const float TWIN_SIZE = 0.68;
const vec2 TWIN_AXIS = vec2(0.6, 0.8);

// A goat's iris goes in these bands from pale around the pupil to dark at its edge
const float GOAT_BANDS = 4.0;
const vec3 GOAT_PALE = vec3(0.8, 0.76, 0.6);
const float GOAT_PALE_SHARE = 0.38;
const float GOAT_EDGE = 0.52;
// How round the ends of its pupil are, and the texels high it stays at least up to where it may narrow to its tip
const float GOAT_PUPIL_ENDS = 0.35;
const float GOAT_PUPIL_LEAST = 0.55;
const float GOAT_PUPIL_TIP = 0.9;
// The dark rim around its opening doubles from this many texels of half width
const float GOAT_RIM_DOUBLE_FROM = 8.0;

// The smallest eyes: a row of pale texels each side of a dark middle, and a dim shorter row above and below. These only ever darken the eye's own colors, for it to stay in the background
const float SPECK_SCLERA = 0.85;
const float SPECK_SHADE = 0.6;
const float SPECK_IRIS = 0.7;
const float SPECK_PUPIL = 0.85;
// A bit of grace for an eye just short of the next texel, and from how many texels out it has room for an iris on both sides of its pupil
const float SPECK_GRACE = 0.1;
const float SPECK_RINGED_FROM = 3.0;
// Lids closer than this, or too few texels of half width for the whole pattern, leave only a dim dash
const float SPECK_SHUT = 0.5;
const float SPECK_WHOLE_FROM = 1.9;

float bow(float x, float power)
{
    return pow(max(1.0 - x * x, 0.0), power);
}

float hash(float value)
{
    return fract(sin(value * 91.3458 + vLids.z * 17.3) * 47453.5453);
}

float random(vec2 cell)
{
    return fract(sin(dot(cell, vec2(12.9898, 78.233)) + vLids.z * 61.7) * 43758.5453);
}

vec3 upperLid(int family, vec2 cell, float above, float thickness, float size)
{
    if (family == ROUND)
        return vLid.rgb * RIM;
    float rim = family == GOAT && size >= GOAT_RIM_DOUBLE_FROM ? 2.0 : 1.0;
    if (size >= LASH_FROM && above < rim)
        return vLid.rgb * LASH;

    float depth = thickness > 0.0 ? above / thickness : 0.0;
    vec3 color = vLid.rgb * mix(LID_NEAR, LID_FAR, depth);
    if (family == HEAVY && thickness >= 4.0 && abs(above - thickness * 0.6) < 0.5)
        color = vLid.rgb * CREASE;
    if (family == NARROW && thickness >= SCALES_FROM)
    {
        vec2 scale = vec2(cell.x + floor(cell.y * 0.5) * 2.0, cell.y);
        if (mod(scale.x, 4.0) < 1.0 || mod(scale.y, 2.0) < 1.0 && mod(scale.x, 4.0) < 2.0)
            color = vLid.rgb * SCALE_DARK;
        else if (mod(scale.x, 4.0) == 2.0 && mod(scale.y, 2.0) >= 1.0)
            color = vLid.rgb * SCALE_LIGHT;
    }
    if ((family == GOAT || family == SLIT) && hash(cell.x) < 0.4 && above > rim)
        color *= FUR_DARK;
    return color;
}

vec3 lowerLid(int family, vec2 cell, float below, float thickness, float size)
{
    if (family == ROUND)
        return vLid.rgb * RIM;
    float rim = family == GOAT && size >= GOAT_RIM_DOUBLE_FROM ? 2.0 : 1.0;
    if (size >= LASH_FROM && below < rim)
        return vLid.rgb * LASH;

    float depth = thickness > 0.0 ? below / thickness : 0.0;
    vec3 color = vLid.rgb * LID_LOWER * mix(1.0, LID_FAR, depth);
    if (family == NARROW && mod(cell.x + cell.y, 3.0) < 1.0)
        color = vLid.rgb * SCALE_DARK;
    return color;
}

vec3 sclera(int family, vec2 unit, vec2 cell, float x, float size, float fromTop)
{
    vec3 color = vSclera.rgb;
    if (size >= DIRT_FROM && random(floor(cell * 0.5)) > 0.7)
        color *= DIRT;

    if ((family == ROUND || family == WRONG) && size >= BULGE_FROM)
    {
        float outward = length(unit) / vReach.x;
        color *= outward > 0.88 ? BULGE_FAR : outward > 0.7 ? BULGE_NEAR : 1.0;
    }
    else if (size >= CORNERS_FROM)
    {
        float edge = abs(x);
        color *= edge > 0.82 ? CORNER_FAR : edge > 0.64 ? CORNER_NEAR : 1.0;
    }

    // Threads running in from the rim, each jumping sideways every few texels
    if (size >= VEINS_FROM && vSclera.a > 0.0)
    {
        float outward = length(unit);
        float run = floor(outward * size / VEIN_RUN);
        float turn = atan(unit.y, unit.x) / 6.28318531 * VEIN_SLICES + (hash(run) - 0.5) * 0.9;
        float slice = floor(turn);
        // Measured in texels across the thread, for it to stay as thin all the way in
        float across = abs(fract(turn) - 0.5) / VEIN_SLICES * 6.28318531 * outward * size;
        if (across < VEIN_THICKNESS && hash(slice + 40.0) < vSclera.a * VEIN_LIMIT && outward > hash(slice + 90.0) * 0.5 + 0.25)
            color = mix(color, VEIN, 0.75);
    }

    if (size >= SHADE_FROM && fromTop < 1.0)
        color *= SHADE_UNDER_LID;
    else if (size >= CORNERS_FROM && fromTop < 2.0)
        color *= SHADE_NEXT;
    return color;
}

// Below one is inside. Cutting the corners keeps a small one from coming out as a square of texels, and gives a slit its points
float pupilShape(vec2 relative)
{
    float across = (abs(relative.x) + abs(relative.y)) / PUPIL_CORNERS;
    return max(dot(relative, relative), across * across);
}

// Below one is inside. A bar rounded off at the ends, where it has the height for it
float goatPupil(vec2 relative, float size)
{
    float along = abs(relative.x);
    if (along >= 1.0)
        return 2.0;

    float tall = vPupil.z * size;
    float high = bow(along, GOAT_PUPIL_ENDS) * tall;
    return abs(relative.y) * tall / max(high, along < GOAT_PUPIL_TIP ? GOAT_PUPIL_LEAST : 0.001);
}

vec3 iris(int family, vec2 toIris, vec2 cell, float size, float fromTop)
{
    float radius = vPupil.x;
    float radiusTexels = radius * size;

    vec3 color = vIris.rgb;
    if (radiusTexels < IRIS_SOLID_BELOW)
        return color * IRIS_SOLID_DARK;

    if (family == GOAT)
    {
        float band = min(floor(length(toIris) / radius * GOAT_BANDS), GOAT_BANDS - 1.0) / (GOAT_BANDS - 1.0);
        color = mix(mix(vIris.rgb, GOAT_PALE, GOAT_PALE_SHARE), vIris.rgb * GOAT_EDGE, band);
    }
    else if (radiusTexels >= RING_FROM && length(toIris) > radius - 1.0 / size)
        color *= IRIS_RING;
    else if (radiusTexels >= BANDS_FROM)
    {
        color *= length(toIris) / radius > 0.72 ? IRIS_OUTER : 1.0;
        if (toIris.y < -radius * 0.42)
            color *= IRIS_CRESCENT;
    }

    if (radiusTexels >= FIBERS_FROM)
    {
        float slice = floor(atan(toIris.y, toIris.x) / 6.28318531 * FIBERS);
        color *= 1.0 + (hash(slice) - 0.5) * FIBER_DEPTH;
    }
    if (radiusTexels >= FLECKS_FROM && random(cell) < FLECK_SHARE)
        color *= hash(cell.x + cell.y * 7.0) > 0.5 ? FLECK_LIGHT : FLECK_DARK;

    // The thin side of a slit or a bar takes a single row off the middle of the even grid, when two would swallow all of a small iris
    vec2 snap = vec2(vPupil.y * size < PUPIL_TWO_ROWS ? 0.5 : 0.0, vPupil.z * size < PUPIL_TWO_ROWS ? 0.5 : 0.0) / size;
    vec2 fromPupil = (toIris + snap - TWIN_AXIS * vPupil.w) / vPupil.yz;
    float inPupil;
    if (family == GOAT)
        inPupil = goatPupil(fromPupil, size);
    else
        inPupil = pupilShape(fromPupil);
    if (family == WRONG)
        inPupil = min(inPupil, pupilShape((toIris + TWIN_AXIS * vPupil.w) / (vPupil.yz * TWIN_SIZE)));

    if (radiusTexels >= BANDS_FROM && family != GOAT && family != NARROW && family != SLIT && inPupil < 2.2 && inPupil >= 1.0)
        color *= IRIS_COLLAR;
    if (inPupil < 1.0)
        color = mix(color, PUPIL, vIris.a * (radiusTexels < PUPIL_SOFT_BELOW ? PUPIL_SOFT : 1.0));

    if (size >= SHADE_FROM && fromTop < 1.0)
        color *= IRIS_UNDER_LID;

    // One texel, or two by two on a large eye, wherever on the grid the iris happens to be
    if (size >= GLINT_FROM)
    {
        vec2 glint = (vLook.xy + vec2(-GLINT_AT, GLINT_AT) * radius) * size;
        vec2 fromGlint = size >= GLINT_LARGE_FROM ? cell + 0.5 - floor(glint + 0.5) : cell - floor(glint);
        if (size >= GLINT_LARGE_FROM ? max(abs(fromGlint.x), abs(fromGlint.y)) < 1.0 : fromGlint == vec2(0.0))
            color = mix(color, GLINT, GLINT_STRENGTH);
    }
    return color;
}

vec4 eye()
{
    vec2 cell = floor(vTexel);
    float size = vStar.z;
    vec2 unit = (cell + 0.5) / size;
    if (dot(unit, unit) > 1.0)
        return vec4(0.0);

    int family = int(vStar.w);
    float x = unit.x * vBend.w;
    float y = unit.y + vBend.y * x * x - vBend.x * x;
    float leaned = clamp(x + vBend.z * (1.0 - x * x), -1.0, 1.0);

    float lids = vLook.z;
    float top = vOpen.x * lids * bow(leaned / vReach.x, vOpen.z);
    float bottom = -vOpen.y * lids * bow(x / vReach.x, vOpen.w);

    float wide = max(lids, 1.0);
    float outerTop = (vOpen.x * wide + vLids.x / size) * bow(leaned / vReach.y, max(vOpen.z, OUTER_SHARPNESS_LEAST));
    float outerBottom = -(vOpen.y * wide + vLids.y / size) * bow(x / vReach.y, max(vOpen.w, OUTER_SHARPNESS_LEAST));
    if (y >= outerTop || y <= outerBottom)
        return vec4(0.0);

    if (y >= top)
        return vec4(upperLid(family, cell, (y - top) * size, outerTop * size - top * size, size), 1.0);
    if (y <= bottom)
        return vec4(lowerLid(family, cell, (bottom - y) * size, bottom * size - outerBottom * size, size), 1.0);

    float fromTop = (top - y) * size;
    vec2 toIris = unit - vLook.xy;
    if (length(toIris) < vPupil.x)
        return vec4(iris(family, toIris, cell, size, fromTop), 1.0);
    return vec4(sclera(family, unit, cell, x, size, fromTop), 1.0);
}

vec4 speck()
{
    vec2 cell = floor(vTexel + 0.5);
    float size = vStar.z;
    float across = abs(cell.x);
    float row = abs(cell.y);
    float reach = floor(size + SPECK_GRACE);
    if (across > reach)
        return vec4(0.0);

    vec3 sclera = vSclera.rgb * SPECK_SCLERA;
    if (vLook.z < SPECK_SHUT || size < SPECK_WHOLE_FROM)
        return row == 0.0 ? vec4(sclera * SPECK_SHADE, 1.0) : vec4(0.0);
    if (row > 1.0 || row == 1.0 && across >= reach)
        return vec4(0.0);

    float fromPupil = abs(cell.x - floor(vLook.x * size + 0.5));
    if (fromPupil == 0.0)
        return vec4(row == 0.0 ? mix(vIris.rgb, PUPIL, SPECK_PUPIL) : vIris.rgb * SPECK_IRIS, 1.0);
    if (row == 1.0)
        return vec4(sclera * SPECK_SHADE, 1.0);
    return vec4(fromPupil == 1.0 && reach >= SPECK_RINGED_FROM ? vIris.rgb * SPECK_IRIS : sclera, 1.0);
}

void main() {
    float star = CurseTime.y * CurseTime.y;
    vec4 plainStar = vec4(star, star, star, 0.0);
    if (vStar.w < 0.0)
    {
        fragColor = plainStar * vStar.x;
        if (dot(fragColor.rgb, vec3(1.0)) <= 0.0)
            discard;
        return;
    }

    bool onStar = abs(vTexel.x) < vStar.y && abs(vTexel.y) < vStar.y;
    fragColor = mix(vLid.a > 0.5 ? speck() : eye(), onStar ? plainStar : vec4(0.0), vStar.x);
    if (fragColor.a <= 0.0 && dot(fragColor.rgb, vec3(1.0)) <= 0.0)
        discard;
}
