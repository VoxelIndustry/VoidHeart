#version 330
#extension GL_ARB_separate_shader_objects : require

#include <voidheart:curse_sky.glsl>

// A mouth at the zenith, drawn on a pixel grid. CurseStateA.w: how far the jaws are apart, 1 being fully open.
// CurseStateB.z: how far the seam has split, CurseStateB.w: how far it has snapped shut.

layout(location = 0) in vec2 vPlane;

layout(location = 0) out vec4 fragColor;

// Half width of the mouth as the tangent of its angular radius, here 35 degrees
const float HALF_WIDTH = 0.7;
// Texels per half width
const float TEXELS = 72.0;

const float UPPER_HEIGHT = 0.5;
const float LOWER_HEIGHT = 0.62;
const float CORNER_POWER = 2.5;
const float BOW_POWER = 0.62;
const float DROOP = 0.09;
const float ASYMMETRY = 0.035;
// The dip in the middle of the upper lip, and the sag of the lower one
const float UPPER_DIP = 0.06;
const float LOWER_SAG = 0.045;
const float DIP_WIDTH = 0.2;
// How far apart the seam's edges stay when the jaws have barely parted
const float SEAM_GAP = 0.006;

const float EDGE_WOBBLE = 0.05;
const float LIP_THICKNESS = 0.17;
const float LIP_THIN_AT_SEAM = 0.012;
const float SKIN_REACH = 0.2;
const float VEIN_DENSITY = 11.0;
const float VEIN_WIDTH = 0.05;
const float VEIN_REACH = 1.3;

const int   ROWS = 3;
const float ROW_SHRINK = 0.235;
const float TEETH_PER_UNIT = 6.2;
const float TOOTH_LENGTH = 0.27;
const float TOOTH_ROOT = 0.035;
const float FANG_CHANCE = 0.2;
const float FANG_LENGTH = 1.7;

const vec3 LIP_HIGHLIGHT = vec3(0.74, 0.34, 0.34);
const vec3 LIP_MID = vec3(0.5, 0.15, 0.2);
const vec3 LIP_DARK = vec3(0.24, 0.06, 0.12);
const vec3 LIP_CREASE = vec3(0.14, 0.03, 0.08);
// All the lips are while still a seam, before the jaws have parted
const vec3 LIP_AT_SEAM = vec3(0.2, 0.04, 0.07);
const float LIP_LIT_BY_OPEN = 0.3;
const vec3 SKIN = vec3(0.07, 0.015, 0.04);
const vec3 VEIN = vec3(0.3, 0.06, 0.11);
const vec3 GUM_LIGHT = vec3(0.47, 0.1, 0.14);
const vec3 GUM = vec3(0.3, 0.05, 0.09);
const vec3 THROAT_RIDGE = vec3(0.13, 0.02, 0.04);
const vec3 THROAT = vec3(0.045, 0.006, 0.012);
const vec3 DEPTHS = vec3(0.0, 0.0, 0.0);
const vec3 FAR_RED = vec3(0.17, 0.012, 0.018);
const vec3 TOOTH_LIGHT = vec3(0.95, 0.9, 0.72);
const vec3 TOOTH_MID = vec3(0.8, 0.72, 0.52);
const vec3 TOOTH_SHADE = vec3(0.54, 0.43, 0.29);
const vec3 TOOTH_ROOT_COLOR = vec3(0.42, 0.22, 0.17);
const vec3 TOOTH_OUTLINE = vec3(0.13, 0.05, 0.05);

float hash(float value)
{
    return fract(sin(value * 127.1) * 43758.5453);
}

float hash2(vec2 value)
{
    return fract(sin(dot(value, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(float value)
{
    float cell = floor(value);
    float along = fract(value);
    along = along * along * (3.0 - 2.0 * along);
    return mix(hash(cell), hash(cell + 1.0), along) * 2.0 - 1.0;
}

float noise2(vec2 value)
{
    vec2 cell = floor(value);
    vec2 along = fract(value);
    along = along * along * (3.0 - 2.0 * along);
    return mix(mix(hash2(cell), hash2(cell + vec2(1.0, 0.0)), along.x),
               mix(hash2(cell + vec2(0.0, 1.0)), hash2(cell + vec2(1.0, 1.0)), along.x), along.y);
}

float bow(float x)
{
    return pow(max(1.0 - pow(abs(x), CORNER_POWER), 0.0), BOW_POWER);
}

// How tooth number row, jaw of a set of teeth covers a point: x along the jaw, depth from the gum toward the throat.
// Returns 0 if not on it, otherwise a shade: > 2 for the outline, 1..2 for the root, 0..1 for the tooth from dark to lit.
float tooth(float x, float depth, float jaw, float row, float reach)
{
    float warped = x * (TEETH_PER_UNIT + row * 1.1) + 0.42 * sin(x * 5.3 + jaw * 2.1 + row * 1.7) + jaw * 0.37 + row * 0.61;
    float cell = floor(warped);
    float along = fract(warped);
    float id = cell * 13.7 + jaw * 71.0 + row * 191.0;

    float tip = 0.5 + (hash(id) - 0.5) * 0.46;
    float gap = 0.04 + hash(id + 3.0) * 0.07;
    float side = along < tip ? (tip - along) / max(tip - gap, 0.01) : (along - tip) / max(1.0 - gap - tip, 0.01);
    if (side > 1.0)
        return 0.0;

    float span = TOOTH_LENGTH * (0.5 + 0.62 * hash(id + 5.0));
    if (hash(id + 7.0) < FANG_CHANCE)
        span *= FANG_LENGTH;
    if (hash(id + 11.0) < 0.12)
        span *= 0.55;
    span *= reach * (1.0 - row * 0.2);

    if (depth < -TOOTH_ROOT)
        return 0.0;
    float profile = span * pow(1.0 - side, 0.82);
    if (depth > profile)
        return 0.0;

    float thickness = (0.8 / TEXELS);
    if (depth > profile - thickness * 1.3 || side > 1.0 - 1.1 / (TEXELS * max(span, 0.02) * 0.6))
        return 2.5;
    if (depth < TOOTH_ROOT * 0.8)
        return 1.5;
    float lit = along < tip ? 0.92 : 0.38;
    return lit + (depth / max(span, 0.01)) * 0.08;
}

vec3 toothColor(float shade, float row)
{
    vec3 color;
    if (shade > 2.0)
        color = TOOTH_OUTLINE;
    else if (shade > 1.0)
        color = TOOTH_ROOT_COLOR;
    else if (shade > 0.7)
        color = shade > 0.96 ? TOOTH_LIGHT : TOOTH_MID;
    else
        color = TOOTH_SHADE;

    float dark = row == 0.0 ? 1.0 : (row == 1.0 ? 0.62 : 0.34);
    return color * dark * mix(vec3(1.0), vec3(1.0, 0.72, 0.68), row * 0.5);
}

void main() {
    float split = CurseStateB.z;
    float snap = CurseStateB.w;
    float open = CurseStateA.w;
    float seconds = CurseTime.x;

    vec2 p = vPlane / HALF_WIDTH;
    vec2 q = (floor(p * TEXELS) + 0.5) / TEXELS;
    float x = q.x;
    float y = q.y;

    // The seam runs out from the middle ahead of the jaws parting, and pulls back in as it shuts
    float reach = smoothstep(0.0, 0.32, split) * (1.0 - smoothstep(0.75, 1.0, snap));
    if (reach <= 0.001)
        discard;

    // Nothing to draw this far out, so most of the quad costs little
    float jawX = x / reach;
    if (abs(jawX) > 1.8 || abs(y) > open + SKIN_REACH + LIP_THICKNESS + 0.1)
        discard;

    float breathe = seconds * 0.45;
    float centre = -DROOP * jawX * jawX * open + ASYMMETRY * sin(jawX * 2.3 + 1.1) * (0.3 + open) + noise(jawX * 9.0 + 3.0) * 0.004;
    float crown = bow(jawX);
    float wobbleUp = noise(jawX * 4.7 + breathe) * 0.6 + noise(jawX * 13.0 - breathe * 0.7) * 0.4;
    float wobbleDown = noise(jawX * 5.3 + 40.0 - breathe * 0.8) * 0.6 + noise(jawX * 14.0 + 17.0 + breathe * 0.6) * 0.4;
    float wobbleScale = EDGE_WOBBLE * (0.12 + open) * crown;
    float dip = exp(-pow(jawX / DIP_WIDTH, 2.0));
    float gap = SEAM_GAP * crown * (1.0 - snap);

    float upper = centre + gap + open * (UPPER_HEIGHT * crown - UPPER_DIP * dip) + wobbleUp * wobbleScale;
    float lower = centre - gap - open * (LOWER_HEIGHT * crown + LOWER_SAG * dip) + wobbleDown * wobbleScale;

    float lipScale = mix(LIP_THIN_AT_SEAM, LIP_THICKNESS, smoothstep(0.0, 0.5, open)) * (1.0 - smoothstep(0.7, 1.0, snap) * 0.7);
    float lipTaper = pow(crown, 0.4);
    float lipUp = lipScale * lipTaper * (0.8 + 0.5 * noise(jawX * 3.1 + 11.0) + 0.16 * noise(jawX * 11.0) + 0.12 * dip);
    float lipDown = lipScale * lipTaper * (1.08 + 0.5 * noise(jawX * 3.4 + 23.0) + 0.18 * noise(jawX * 12.0 + 5.0) + 0.3 * dip);

    vec3 color;
    float alpha = 1.0;

    bool inside = abs(jawX) < 1.0 && y < upper && y > lower;
    bool onUpperLip = abs(jawX) < 1.0 && y >= upper && y < upper + lipUp;
    bool onLowerLip = abs(jawX) < 1.0 && y <= lower && y > lower - lipDown;

    if (inside)
    {
        float halfHeight = max((upper - lower) * 0.5, 0.001);
        float middle = (upper + lower) * 0.5;
        float ring = sqrt(jawX * jawX + pow((y - middle) / max(open * 0.66, 0.02), 2.0));
        ring = clamp(ring, 0.0, 1.0);

        // Gums at the rim going down a ribbed throat to black, with a last red far inside
        float ribs = sin(pow(ring, 0.7) * 34.0 + noise(atan(y - middle, x) * 5.0) * 2.2);
        vec3 throat = mix(THROAT, THROAT_RIDGE, step(0.45, ribs) * smoothstep(0.26, 0.6, ring));
        color = ring > 0.48 ? mix(throat, GUM, smoothstep(0.6, 0.92, ring)) : mix(DEPTHS, throat, smoothstep(0.2, 0.44, ring));
        if (ring > 0.82 && noise2(q * 38.0) > 0.66)
            color = GUM_LIGHT;
        color = mix(color, FAR_RED, (1.0 - smoothstep(0.03, 0.17, ring)) * (0.75 + 0.25 * sin(seconds * 1.2)));

        float depthUp = upper - y;
        float depthDown = y - lower;
        // Shrunken copies of the jaws, back to front, so that inner rows sink toward the throat. The outer row is drawn last
        for (int i = ROWS - 1; i >= 0; i--)
        {
            float row = float(i);
            float shrink = 1.0 - row * ROW_SHRINK;
            float rowX = jawX / shrink;
            if (abs(rowX) >= 1.0)
                continue;

            float rowCrown = bow(rowX);
            float rowCentre = centre * shrink;
            float rowUpper = rowCentre + open * UPPER_HEIGHT * rowCrown * shrink + wobbleUp * wobbleScale * shrink;
            float rowLower = rowCentre - open * LOWER_HEIGHT * rowCrown * shrink + wobbleDown * wobbleScale * shrink;
            float teethReach = min(1.0, open * 1.7) * pow(rowCrown, 0.75);

            float rake = rowX * (rowUpper - y) * 0.45;
            float up = tooth(rowX + rake, rowUpper - y, 0.0, row, teethReach);
            if (up > 0.0)
                color = toothColor(up, row);

            rake = rowX * (y - rowLower) * 0.45;
            float down = tooth(rowX + rake, y - rowLower, 1.0, row, teethReach);
            if (down > 0.0)
                color = toothColor(down, row);
        }
    }
    else if (onUpperLip || onLowerLip)
    {
        float across = onUpperLip ? (y - upper) / max(lipUp, 0.001) : (lower - y) / max(lipDown, 0.001);
        // Folds run across the lip at uneven slants, a wet shine along it comes and goes
        float folds = noise(jawX * 21.0 + (onUpperLip ? 0.0 : 9.0) + across * (1.2 + noise(jawX * 2.0) * 2.4));
        float bulge = noise2(vec2(jawX * 5.5, across * 2.2 + (onUpperLip ? 0.0 : 7.0)));
        color = across < 0.16 ? LIP_DARK : (across > 0.84 - bulge * 0.12 ? LIP_DARK : LIP_MID);
        if (across > 0.26 && across < 0.42 + bulge * 0.3)
            color = LIP_HIGHLIGHT;
        if (folds > 0.56)
            color = LIP_CREASE;
        color = mix(LIP_AT_SEAM, color, smoothstep(0.0, LIP_LIT_BY_OPEN, open));
    }
    else
    {
        // Bruised, torn skin hugging the lips, thrown over the sky in hard dither, with veins reaching out
        float above = y > centre ? y - upper - lipUp : lower - lipDown - y;
        float beyond = max(abs(jawX) - 1.0, 0.0) * reach * 1.3;
        float away = max(above, 0.0) + beyond;
        float skinReach = SKIN_REACH * (0.2 + 0.8 * smoothstep(0.0, 0.7, open)) * (1.0 - snap);
        float skin = 1.0 - away / max(skinReach, 0.001) + noise2(q * 7.0) * 0.45 - 0.2;

        vec2 cell = floor(p * TEXELS);
        vec2 bayerCell = mod(cell, 4.0);
        float bayer = mod(bayerCell.x * 8.0 + bayerCell.y * 4.0 + mod(bayerCell.x + bayerCell.y, 2.0) * 2.0 + bayerCell.y, 16.0) / 16.0;

        float angle = atan(y - centre, x);
        float strands = noise(angle * VEIN_DENSITY + noise2(q * 4.0) * 3.2);
        bool vein = abs(strands) < VEIN_WIDTH * (1.0 - away / max(skinReach * VEIN_REACH, 0.001)) && away < skinReach * VEIN_REACH;

        if (vein)
            color = VEIN * (0.55 + 0.45 * clamp(skin + 0.5, 0.0, 1.0));
        else if (skin > bayer)
            color = SKIN * (0.75 + 0.5 * noise2(q * 19.0));
        else
            discard;
    }

    fragColor = vec4(color, alpha);
}
