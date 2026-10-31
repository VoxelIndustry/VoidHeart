#version 330
#extension GL_ARB_separate_shader_objects : require

#include <voidheart:oculus.glsl>

uniform sampler2D FootprintMask;

layout(location = 0) in vec3 holoPos;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) flat in uint face;

layout(location = 0) out vec4 fragColor;

// Down, up, north, south, west, east
const float FACE_SHADE[6] = float[](0.45, 1.0, 0.8, 0.8, 0.65, 0.65);
const vec3 CUT_COLOR = vec3(0.06, 0.16, 0.14);

void main() {
    vec2 footprintUv = holoPos.xz / HoloGeometry.xy;
    if (any(lessThan(footprintUv, vec2(0.0))) || any(greaterThan(footprintUv, vec2(1.0))) || texture(FootprintMask, footprintUv).r < 0.5) {
        discard;
    }

    vec3 color = gl_FrontFacing ? vertexColor.rgb * FACE_SHADE[face] : CUT_COLOR;
    fragColor = vec4(color, 1.0);
}
