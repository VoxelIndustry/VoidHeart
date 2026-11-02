#version 330
#extension GL_ARB_separate_shader_objects : require

#include <voidheart:curse_sky.glsl>
#include <voidheart:curse_omen_wave.glsl>

layout(location = 0) in vec2 corner;
layout(location = 1) in float turned;
layout(location = 2) in float glow;

layout(location = 0) out vec4 fragColor;

// A deep arterial red, with a soft core so that a star moving slowly doesn't step from pixel to pixel
const vec3 CORE_COLOR = vec3(0.88, 0.012, 0.035);
const vec3 HALO_COLOR = vec3(0.5, 0.004, 0.014);
const float CORE_TIGHTNESS = 15.0;
const float HALO_TIGHTNESS = 4.5;
const float EDGE_FADE_FROM = 0.72;

void main() {
    float vanilla = CurseTime.y;
    if (turned <= 0.0) {
        fragColor = vec4(vanilla);
        return;
    }

    float radius = length(corner) / OMEN_STAR_BLOOM;
    float edge = 1.0 - smoothstep(EDGE_FADE_FROM, 1.0, radius);
    vec3 red = (CORE_COLOR * exp(-radius * radius * CORE_TIGHTNESS) + HALO_COLOR * exp(-radius * radius * HALO_TIGHTNESS)) * edge * glow;

    // What is left of the vanilla star, as bright as vanilla drew it. Leaves quickly, so that a turning star never looks pink
    float white = (1.0 - turned) * (1.0 - turned) * step(max(abs(corner.x), abs(corner.y)), 1.0);
    fragColor = vec4(vec3(vanilla * vanilla) * white + red * turned, 1.0);
}
