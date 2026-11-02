#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <voidheart:curse_sky.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec3 Center;
layout(location = 2) in vec4 Corner;
layout(location = 3) in vec4 Seed;

void main() {
    gl_Position = ProjMat * StarView * vec4(Position, 1.0);
}
