#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <voidheart:oculus.glsl>

layout(location = 0) in uvec4 Position;
layout(location = 1) in vec4 Color;

layout(location = 0) out vec3 holoPos;
layout(location = 1) out vec4 vertexColor;
layout(location = 2) flat out uint face;

void main() {
    vec3 world = vec3(Position.xyz) + ChunkOffset.xyz;
    vec3 holo = world * HoloGeometry.z;
    holo.xz += HoloGeometry.xy * 0.5;

    gl_Position = ProjMat * HoloModelView * vec4(holo, 1.0);
    holoPos = holo;
    vertexColor = Color;
    face = Position.w;
}
