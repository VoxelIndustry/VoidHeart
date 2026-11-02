#version 330
#extension GL_ARB_separate_shader_objects : require

// Lit as blocks are, by what was worked out for each corner, and by a dim light of its own so that it can be seen at night.

uniform sampler2D Sampler0;

layout(location = 0) in vec2 vUv;
layout(location = 1) in vec3 vColor;

layout(location = 0) out vec4 fragColor;

const float GAIN = 1.45;
const vec3 SELF_LIGHT = vec3(0.09, 0.02, 0.025);

void main() {
    vec3 albedo = texture(Sampler0, vUv).rgb;
    fragColor = vec4(albedo * (vColor * GAIN + SELF_LIGHT), 1.0);
}
