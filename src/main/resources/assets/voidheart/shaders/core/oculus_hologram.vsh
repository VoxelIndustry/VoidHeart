#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <voidheart:oculus.glsl>

layout(location = 0) in uvec4 Position;
layout(location = 1) in vec4 Color;

layout(location = 0) out vec3 holoPos;
layout(location = 1) out vec4 vertexColor;
layout(location = 2) flat out uint face;

// Down, up, north, south, west, east
const vec3 FACE_NORMAL[6] = vec3[](vec3(0, -1, 0), vec3(0, 1, 0), vec3(0, 0, -1), vec3(0, 0, 1), vec3(-1, 0, 0), vec3(1, 0, 0));
// Table blocks hollow faces stand out of their voxel, in front of the faces of the rock around them
const float HOLLOW_OUTSET = 0.002;

void main() {
    vec3 world = vec3(Position.xyz) + ChunkOffset.xyz;
    vec3 holo = world * HoloGeometry.z;
    holo.xz += HoloGeometry.xy * 0.5;
    if (Color.a < HOLLOW_ALPHA_LIMIT && Position.w < 6u) {
        holo += FACE_NORMAL[Position.w] * HOLLOW_OUTSET;
    }

    gl_Position = ProjMat * HoloModelView * vec4(holo, 1.0);
    holoPos = holo;
    vertexColor = Color;
    face = Position.w;
}
