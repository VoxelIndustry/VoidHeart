#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <voidheart:curse_sky.glsl>

// One large quad straight overhead in the still sky. Nothing changes as the camera turns since it never leaves the center, so it can stand for whatever is deep behind it.

layout(location = 0) in vec3 Position;

layout(location = 0) out vec2 vPlane;

// The same as TEAR_EXTENT in RaptureMassShape
const float EXTENT = 1.44;
const float HEIGHT = 40.0;

void main() {
    vPlane = Position.xz * EXTENT;
    gl_Position = ProjMat * SkyView * vec4(vPlane.x * HEIGHT, HEIGHT, vPlane.y * HEIGHT, 1.0);
    // At the very end of the depth range, so that any cube already drawn stays in front
#ifdef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
    gl_Position.z = 0.0;
#else
    gl_Position.z = -gl_Position.w;
#endif
}
