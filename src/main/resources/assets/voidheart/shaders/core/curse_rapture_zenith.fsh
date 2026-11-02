#version 330
#extension GL_ARB_separate_shader_objects : require

#include <voidheart:curse_sky.glsl>

// Puts the mouth worked out in curse_rapture_zenith_bake into the sky, one texel to a cell of its pixel grid.

uniform sampler2D Sampler0;

layout(location = 0) in vec2 vPlane;

layout(location = 0) out vec4 fragColor;

// Half width of the mouth as the tangent of its angular radius, here 35 degrees
const float HALF_WIDTH = 0.7;
// The same as in curse_rapture_zenith_bake
const float TEXELS = 72.0;
const float BAKED_SIZE = 206.0;

void main() {
    vec4 color = texture(Sampler0, vPlane / HALF_WIDTH * TEXELS / BAKED_SIZE + 0.5);
    if (color.a <= 0.0)
        discard;
    fragColor = color;
}
