#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>
#include <voidheart:oculus.glsl>

// How far each texel is from leaving the table footprint: 0 outside, 1 past the fade range
uniform sampler2D FootprintMask;

layout(location = 0) in vec3 holoPos;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) flat in uint face;

layout(location = 0) out vec4 fragColor;

// Down, up, north, south, west, east
const float FACE_SHADE[6] = float[](0.45, 1.0, 0.8, 0.8, 0.65, 0.65);
const vec3 CUT_COLOR = vec3(0.14, 0.36, 0.34);
const vec3 GLOW_COLOR = vec3(0.45, 0.95, 0.9);
const vec3 HOLOGRAM_TINT = vec3(0.88, 1.0, 1.06);
// Table blocks under the window top over which the land fades out
const float TOP_FADE = 0.12;
// Above one, the outer part of the fade keeps all its pixels and only glows
const float DITHER_REACH = 2.0;
const float BAYER[16] = float[](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0, 3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);

void main() {
    // Meshes still shown from a previous window may reach past the current one
    if (holoPos.y < 0.0 || holoPos.y > HoloGeometry.w) {
        discard;
    }

    vec2 footprintUv = holoPos.xz / HoloGeometry.xy;
    if (any(lessThan(footprintUv, vec2(0.0))) || any(greaterThan(footprintUv, vec2(1.0)))) {
        discard;
    }

    // Fading by discarding a growing share of a fixed pattern keeps the hologram opaque: no sorting, no blending.
    // The glow starts further in than the holes so the pixels left near the edge carry it
    float fade = min(texture(FootprintMask, footprintUv).r, clamp((HoloGeometry.w - holoPos.y) / TOP_FADE, 0.0, 1.0));
    ivec2 pixel = ivec2(gl_FragCoord.xy) & 3;
    if (fade * DITHER_REACH < (BAYER[pixel.x + pixel.y * 4] + 0.5) / 16.0) {
        discard;
    }
    float glow = 1.0 - smoothstep(0.0, 1.0, fade);

    // Back faces are only seen through the cut sides of the land. They lie deeper than the cut itself, so any shading
    // from their own position would read as a pit: a flat color reads as a solid cross section at whatever depth
    if (!gl_FrontFacing) {
        fragColor = vec4(CUT_COLOR, 1.0);
        return;
    }

    vec3 color = vertexColor.rgb * FACE_SHADE[face] * HOLOGRAM_TINT;
    float ticks = GameTime * 24000.0;
    float scanline = 1.0 - smoothstep(0.0, 0.04, abs(fract(holoPos.y * 5.0 - ticks * 0.015) - 0.5));
    color += GLOW_COLOR * scanline * 0.12;
    color = mix(color, GLOW_COLOR, glow * 0.85);

    fragColor = vec4(color, 1.0);
}
