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
const uint FLOOR_FACE = 6u;
// The land is seen through a violet haze, as in a scrying pool
const vec3 HAZE = vec3(0.42, 0.24, 0.62);
const float HAZE_STRENGTH = 0.28;
const vec3 VOID_COLOR = vec3(0.035, 0.012, 0.06);
const vec3 MOTE_COLOR = vec3(0.78, 0.45, 1.0);
const vec3 STAR_COLOR = vec3(0.85, 0.7, 1.0);
// Table blocks under the window top over which the land sinks into the void
const float TOP_FADE = 0.12;
// Above one, the outer part of the fade keeps all its pixels and only darkens
const float DISSOLVE_REACH = 1.6;

float hash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 p) {
    vec3 cell = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(cell), hash(cell + vec3(1, 0, 0)), f.x),
                   mix(hash(cell + vec3(0, 1, 0)), hash(cell + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(cell + vec3(0, 0, 1)), hash(cell + vec3(1, 0, 1)), f.x),
                   mix(hash(cell + vec3(0, 1, 1)), hash(cell + vec3(1, 1, 1)), f.x), f.y), f.z);
}

void main() {
    // Meshes still shown from a previous window may reach past the current one
    if (holoPos.y < 0.0 || holoPos.y > HoloGeometry.w) {
        discard;
    }

    vec2 footprintUv = holoPos.xz / HoloGeometry.xy;
    if (any(lessThan(footprintUv, vec2(0.0))) || any(greaterThan(footprintUv, vec2(1.0)))) {
        discard;
    }

    float seconds = GameTime * 1200.0;

    // Toward the table edges and the window top, the land frays away along drifting noise: still opaque, no sorting
    float fade = min(texture(FootprintMask, footprintUv).r, clamp((HoloGeometry.w - holoPos.y) / TOP_FADE, 0.0, 1.0));
    float drift = noise(holoPos * 24.0 + vec3(0.0, seconds * 0.35, 0.0));
    if (fade * DISSOLVE_REACH < drift) {
        discard;
    }
    float rim = 1.0 - smoothstep(0.0, 1.0, fade);

    // Back faces are only seen through the cut sides of the land, and the floor lies where the window bottom is empty:
    // both are windows into the void, its stars fixed to the screen
    if (!gl_FrontFacing || face == FLOOR_FACE) {
        vec2 cell = floor(gl_FragCoord.xy / 2.0);
        float star = step(0.994, hash(vec3(cell, 7.0)));
        float twinkle = 0.5 + 0.5 * sin(seconds * 2.0 + hash(vec3(cell, 3.0)) * 40.0);
        fragColor = vec4(VOID_COLOR + STAR_COLOR * star * twinkle * 0.8, 1.0);
        return;
    }

    vec3 color = vertexColor.rgb * FACE_SHADE[face];
    color = mix(color, color * HAZE * 1.6, HAZE_STRENGTH);

    // Slow ripples spread from the middle of the table across the land, like a disturbed pool: warped by drifting
    // noise so they never form clean rings, and fading in and out unevenly along their fronts
    vec2 flow = holoPos.xz * 1.6;
    float warp = noise(vec3(flow, seconds * 0.12)) * 1.1 + noise(vec3(flow * 2.7, seconds * 0.21 + 5.0)) * 0.35;
    float distance = length(holoPos.xz - HoloGeometry.xy * 0.5) + warp;
    float strength = smoothstep(0.25, 0.8, noise(vec3(flow * 0.9, seconds * 0.08 + 11.0)));
    float ripple = (sin(distance * 9.0 - seconds * 1.4) * 0.5 + 0.5) * strength;
    color *= 0.94 + 0.09 * ripple;
    color += HAZE * pow(ripple, 6.0) * 0.07;

    // Near the edges the land sinks into darkness, lit by sparse violet motes
    color = mix(color, VOID_COLOR, rim * 0.85);
    float mote = step(0.93, noise(holoPos * 60.0 + vec3(seconds * 0.2, seconds * 0.5, 0.0)));
    color += MOTE_COLOR * mote * rim * 0.9;

    fragColor = vec4(color, 1.0);
}
