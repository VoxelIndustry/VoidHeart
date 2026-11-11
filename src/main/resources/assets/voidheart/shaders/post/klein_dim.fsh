#version 330
#extension GL_ARB_separate_shader_objects : require

// Darkens and drains the scene around a Klein Star so the star is the only bright thing in it, then washes the view out when it bursts.
// What is already nearly white, the star and its arcs, and what lies inside the star's glare on screen, is left alone.

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform KleinDim {
    // xy: the star on screen, z: the radius of its glare in screen heights
    vec4 Spot;
    // x: how much darker, y: how washed out, z: how drained of color
    vec4 Levels;
};

layout(location = 0) out vec4 fragColor;

const vec3 FLASH_COLOR = vec3(1.0, 0.97, 0.88);
const vec3 DIM_TINT = vec3(0.92, 0.9, 1.0);

void main() {
    vec4 color = texture(InSampler, texCoord);
    float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));

    vec2 fromSpot = (texCoord - Spot.xy) * vec2(OutSize.x / OutSize.y, 1.0);
    float glare = Spot.z <= 0.0 ? 0.0 : 1.0 - smoothstep(Spot.z * 0.35, Spot.z, length(fromSpot));
    float bright = smoothstep(0.9, 1.0, min(color.r, min(color.g, color.b)));
    float keep = max(glare, bright);

    vec3 dimmed = mix(color.rgb, vec3(luma), Levels.z * Levels.x) * DIM_TINT * (1.0 - Levels.x);
    vec3 result = mix(dimmed, color.rgb, keep);
    result = mix(result, FLASH_COLOR, Levels.y);
    fragColor = vec4(result, 1.0);
}
