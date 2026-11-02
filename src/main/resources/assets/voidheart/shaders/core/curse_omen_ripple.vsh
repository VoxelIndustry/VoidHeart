#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <voidheart:curse_sky.glsl>

layout(location = 0) in vec3 Position;

layout(location = 0) out vec3 skyPos;

void main() {
    skyPos = Position;
    gl_Position = ProjMat * SkyView * vec4(Position, 1.0);
}
