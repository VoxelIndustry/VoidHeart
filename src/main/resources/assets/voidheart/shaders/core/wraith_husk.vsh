#version 330
#extension GL_ARB_separate_shader_objects : require

// Vanilla's entity vertex stage, passing on what a raised wraith's husk needs: the vertex's place relative to the camera, the face's normal for
// its rim, and where the body's feet are and how tall it is, carried in the overlay coordinates as 64ths of a block, so it can fray from below.

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
layout(location = 7) out vec2 body;
layout(location = 8) out vec3 viewPos;
layout(location = 9) out vec3 faceNormal;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color;
    texCoord0 = UV0;
    // Feet height relative to the camera, offset to stay positive, and the body's height
    body = vec2(float(UV1.x) - 32768.0, float(UV1.y)) / 64.0;
    viewPos = Position;
    faceNormal = Normal;
}
