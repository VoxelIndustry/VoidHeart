#version 330
#extension GL_ARB_separate_shader_objects : require

#include <voidheart:curse_sky.glsl>

layout(location = 0) in vec2 vUv;
// x, y: where the iris looks. z: how far the lids part. w: how much it stares
layout(location = 1) flat in vec4 vLook;
// x: how much of the plain star is left. y: pupil size as a share of the iris. z, w: random values for this eye
layout(location = 2) flat in vec4 vEye;

layout(location = 0) out vec4 fragColor;

// The eye is drawn on its own pixel grid: texels per half width. All lengths below are in half widths
const float TEXELS = 7.0;
// The same as in the vertex shader
const float ASPECT = 0.86;

const float OPENING_TOP = 0.48;
const float OPENING_BOTTOM = 0.33;
const float SLANT = 0.1;
const float LASH_LINE = 1.2 / TEXELS;
const float LID_TOP = 1.3 / TEXELS;
const float LID_BOTTOM = 1.0 / TEXELS;

const float IRIS_RADIUS = 0.5;
// The glow fills the dark iris from below, up to this far above its center, leaving it dark under the lid
const float GLOW_LINE = 0.4;
const float GLOW_LINE_STARING = 0.24;
const float GLINT_OFFSET = 1.5 / TEXELS;

const vec3 SCLERA_LIGHT = vec3(0.91, 0.86, 0.73);
const vec3 SCLERA_DIRTY = vec3(0.8, 0.71, 0.58);
const vec3 SCLERA_SHADOW = vec3(0.6, 0.48, 0.45);
const vec3 VEIN = vec3(0.64, 0.2, 0.25);
const vec3 IRIS_RIM = vec3(0.04, 0.13, 0.07);
const vec3 IRIS_DARK = vec3(0.09, 0.33, 0.16);
const vec3 IRIS_GLOW = vec3(0.31, 0.84, 0.33);
const vec3 IRIS_HOT = vec3(0.72, 1.0, 0.54);
const vec3 PUPIL = vec3(0.015, 0.015, 0.035);
const vec3 LID_LIGHT = vec3(0.58, 0.22, 0.37);
const vec3 LID = vec3(0.4, 0.15, 0.3);
const vec3 LASH = vec3(0.23, 0.07, 0.17);

float random(vec2 texel)
{
    return fract(sin(dot(texel, vec2(12.9898, 78.233)) + vEye.z * 61.7) * 43758.5453);
}

float bow(float x, float power)
{
    return pow(max(1.0 - x * x, 0.0), power);
}

void main() {
    float star = CurseTime.y * CurseTime.y;
    vec4 plainStar = vec4(star, star, star, 0.0);
    if (vEye.x >= 1.0)
    {
        fragColor = plainStar;
        return;
    }

    vec2 cell = floor(vUv * vec2(TEXELS, TEXELS * ASPECT));
    vec2 texel = (cell + 0.5) / TEXELS;

    float lids = vLook.z;
    float x = texel.x;
    float edge = abs(x);
    float y = texel.y - (vEye.w - 0.5) * 2.0 * SLANT * x;
    float top = (OPENING_TOP + (vEye.z - 0.5) * 0.1) * lids * bow(x, 0.7);
    float bottom = -OPENING_BOTTOM * lids * bow(x, 0.9);

    vec4 eye = vec4(0.0);
    if (edge < 1.0)
    {
        if (y < top && y > bottom)
        {
            // Pale and slightly dirty in patches, in shade under the upper lid, with blood toward the corners
            vec3 color = step(0.68, random(floor(cell * 0.5))) > 0.5 ? SCLERA_DIRTY : SCLERA_LIGHT;
            if (top - y < 1.0 / TEXELS)
                color = SCLERA_SHADOW;
            if (edge > 0.55 && random(cell + 7.0) > 1.16 - edge * 0.6)
                color = VEIN;

            vec2 toIris = texel - vLook.xy;
            float radius = length(toIris) / IRIS_RADIUS;
            if (radius < 1.0)
            {
                float height = toIris.y / IRIS_RADIUS;
                color = radius > 0.82 ? IRIS_RIM : IRIS_DARK;
                if (radius <= 0.86 && height < mix(GLOW_LINE, GLOW_LINE_STARING, vLook.w))
                    color = IRIS_GLOW * mix(0.85, 1.15, vLook.w);
                if (radius < vEye.y + 0.2 && height < -0.1)
                    color = IRIS_HOT;
                if (radius < vEye.y)
                    color = PUPIL;
                if (length(toIris - vec2(-GLINT_OFFSET, GLINT_OFFSET)) < 0.65 / TEXELS)
                    color = SCLERA_LIGHT;
            }
            eye = vec4(color, 1.0);
        }
        else if (y >= top)
        {
            float above = y - top;
            if (above < LASH_LINE)
                eye = vec4(LASH, 1.0);
            else if (above < LASH_LINE + LID_TOP * bow(x, 0.4))
                eye = vec4(above < LASH_LINE + 1.0 / TEXELS ? LID_LIGHT : LID, 1.0);
        }
        else
        {
            float below = bottom - y;
            if (below < LASH_LINE * 0.8)
                eye = vec4(LASH, 1.0);
            else if (below < LASH_LINE * 0.8 + LID_BOTTOM * bow(x, 0.4))
                eye = vec4(LID, 1.0);
        }
    }

    fragColor = mix(eye, plainStar, vEye.x);
    if (fragColor.a <= 0.0 && dot(fragColor.rgb, vec3(1.0)) <= 0.0)
        discard;
}
