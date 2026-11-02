#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <voidheart:curse_sky.glsl>
#include <voidheart:curse_omen_wave.glsl>

layout(location = 0) in vec3 Position;

layout(location = 0) out vec3 skyPos;
layout(location = 1) out vec3 fields;
layout(location = 2) out float strength;
layout(location = 3) flat out vec4 sourceWave[WAVE_SOURCES];
layout(location = 7) flat out vec4 sourceShape[WAVE_SOURCES];

// Over this height of the unit sphere, the waves die out toward the horizon
const float HORIZON_LOW = 0.08;
const float HORIZON_HIGH = 0.5;
// As chords of the unit sphere: the waves pass around the moon, leaving it as it is
const float MOON_CLEAR = 0.24;
const float MOON_EDGE = 0.46;

void main() {
    vec3 dir = normalize(Position);
    // Where SkyRenderer places the moon
    vec3 moon = vec3(-sin(CurseStateA.w), cos(CurseStateA.w), 0.0);
    strength = CurseStateA.z * smoothstep(HORIZON_LOW, HORIZON_HIGH, dir.y) * smoothstep(MOON_CLEAR, MOON_EDGE, length(dir - moon));
    fields = curseOmenFields(dir);
    curseOmenSources(sourceWave, sourceShape);

    skyPos = Position;
    gl_Position = ProjMat * SkyView * vec4(Position, 1.0);
}
