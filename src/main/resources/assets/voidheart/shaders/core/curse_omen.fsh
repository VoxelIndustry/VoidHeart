#version 330
#extension GL_ARB_separate_shader_objects : require

#include <voidheart:curse_sky.glsl>

layout(location = 0) out vec4 fragColor;

void main() {
    fragColor = vec4(1.0, 0.1, 0.1, 1.0);
}
