#version 330
#extension GL_ARB_separate_shader_objects : require

#include <voidheart:curse_sky.glsl>

// Fills the small texture the mouth is worked out into, each of its texels being one cell of the mouth's grid.

layout(location = 0) in vec3 Position;

layout(location = 0) out vec2 vCell;

// The same as MOUTH_BAKED_SIZE in RaptureCurse
const float BAKED_SIZE = 206.0;

void main() {
    vCell = Position.xz * BAKED_SIZE * 0.5;
    gl_Position = vec4(Position.x, Position.z, 0.0, 1.0);
}
