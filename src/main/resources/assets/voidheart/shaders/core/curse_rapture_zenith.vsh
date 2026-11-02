#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <voidheart:curse_sky.glsl>

// One large quad straight overhead in the still sky, turned so that +y of the plane is the top of the view
// of someone who was facing (CurseStateB.x, CurseStateB.y as cos, sin of their yaw) and tilted their head back.

layout(location = 0) in vec3 Position;

layout(location = 0) out vec2 vPlane;

const float HEIGHT = 50.0;

void main() {
    vec3 right = vec3(-CurseStateB.x, 0.0, -CurseStateB.y);
    vec3 back = vec3(CurseStateB.y, 0.0, -CurseStateB.x);
    vec3 world = (right * Position.x + back * Position.z + vec3(0.0, 1.0, 0.0)) * HEIGHT;

    vPlane = Position.xz;
    gl_Position = ProjMat * SkyView * vec4(world, 1.0);
}
