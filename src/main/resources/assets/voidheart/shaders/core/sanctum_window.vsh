#version 330
#extension GL_ARB_separate_shader_objects : require

// Vanilla's entity vertex stage, passing on what the window into a sanctum needs to trace its view: the vertex's place relative to the camera,
// its place on the door in blocks from the door's lower left corner, carried in UV1 as 64ths of a block, and the face's outward normal.

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 6) out vec2 texCoord0;
layout(location = 7) out vec2 doorPos;
layout(location = 8) out vec3 viewPos;
layout(location = 9) out vec3 faceNormal;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color;
    texCoord0 = UV0;
    doorPos = vec2(UV1) / 64.0;
    viewPos = Position;
    faceNormal = Normal;
}
