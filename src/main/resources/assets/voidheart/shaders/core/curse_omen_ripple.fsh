#version 330
#extension GL_ARB_separate_shader_objects : require

#include <voidheart:curse_sky.glsl>
#include <voidheart:curse_omen_wave.glsl>

layout(location = 0) in vec3 skyPos;
layout(location = 1) in vec3 fields;
layout(location = 2) in float strength;
layout(location = 3) flat in vec4 sourceWave[WAVE_SOURCES];
layout(location = 7) flat in vec4 sourceShape[WAVE_SOURCES];

layout(location = 0) out vec4 fragColor;

// Additive light, kept dim enough for the stars to stand far above it
const vec3 CREST_COLOR = vec3(0.24, 0.005, 0.01);
const vec3 FLANK_COLOR = vec3(0.14, 0.003, 0.014);

void main() {
    if (strength <= 0.0) {
        discard;
    }

    float wave = curseOmenRings(normalize(skyPos), fields, sourceWave, sourceShape) * strength;
    vec3 color = mix(FLANK_COLOR, CREST_COLOR, wave) * wave;
    // Such dim gradients would otherwise band into contours on 8 bits
    float dither = (curseOmenHash(vec3(gl_FragCoord.xy, 1.0)) - 0.5) / 255.0;
    fragColor = vec4(max(color + dither * step(0.002, wave), 0.0), 1.0);
}
