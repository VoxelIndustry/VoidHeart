#version 330
#extension GL_ARB_separate_shader_objects : require

#include <voidheart:curse_sky.glsl>
#include <voidheart:curse_omen_wave.glsl>

layout(location = 0) in vec3 skyPos;

layout(location = 0) out vec4 fragColor;

// Additive light, kept dim enough for the stars to stand far above it
const vec3 CREST_COLOR = vec3(0.24, 0.005, 0.01);
const vec3 FLANK_COLOR = vec3(0.14, 0.003, 0.014);
// Over this height of the unit sphere, the waves die out toward the horizon
const float HORIZON_LOW = 0.08;
const float HORIZON_HIGH = 0.5;
// As chords of the unit sphere: the waves pass around the moon, leaving it as it is
const float MOON_CLEAR = 0.24;
const float MOON_EDGE = 0.46;

void main() {
    vec3 dir = normalize(skyPos);
    // Where SkyRenderer places the moon
    vec3 moon = vec3(-sin(CurseStateA.w), cos(CurseStateA.w), 0.0);
    float strength = CurseStateA.z * smoothstep(HORIZON_LOW, HORIZON_HIGH, dir.y) * smoothstep(MOON_CLEAR, MOON_EDGE, length(dir - moon));
    if (strength <= 0.0) {
        discard;
    }

    float wave = curseOmenWave(dir) * strength;
    vec3 color = mix(FLANK_COLOR, CREST_COLOR, wave) * wave;
    // Such dim gradients would otherwise band into contours on 8 bits
    float dither = (curseOmenHash(vec3(gl_FragCoord.xy, 1.0)) - 0.5) / 255.0;
    fragColor = vec4(max(color + dither * step(0.002, wave), 0.0), 1.0);
}
