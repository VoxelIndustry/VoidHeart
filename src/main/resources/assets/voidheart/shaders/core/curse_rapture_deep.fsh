#version 330
#extension GL_ARB_separate_shader_objects : require

#include <voidheart:curse_rapture_mass_common.glsl>

// The sky's tear and the painted gullet behind it sit on one coarse grid fixed in the sky, the eye at its very center on one of its own.

uniform sampler2D Sampler0; // Red: the opening at which it tears, green: the crack itself, blue: the opening at which that crack is swallowed
uniform sampler2D Sampler1; // The gullet
uniform sampler2D Sampler2; // The eye, its alpha telling what each texel is

layout(location = 0) in vec2 vPlane;

layout(location = 0) out vec4 fragColor;

// The same as in RaptureMassShape
const float CELL = 0.0045;
const float TEAR_EXTENT = 1.44;
// The same as in the painting of the gullet
const float DEEP_EXTENT = 0.72;

// A share of the opening over which a fresh edge still looks raw
const float RAW_SPAN = 0.06;
const float RAW_STEPS = 3.0;
const vec3 RAW = vec3(0.34, 0.055, 0.05);
const vec3 CRACK_LIP = vec3(0.1, 0.016, 0.018);

void main() {
    vec2 cell = (floor(vPlane / CELL) + 0.5) * CELL;

    vec4 tear = texture(Sampler0, cell / (1.0 + BreachMotion.z) / TEAR_EXTENT * 0.5 + 0.5);
    float opensAt = tear.r;
    float open = BreachCore.x;
    if (open <= 0.0 || opensAt >= 0.999 || opensAt > open)
        discard;

    float fresh = ceil((1.0 - clamp((open - opensAt) / RAW_SPAN, 0.0, 1.0)) * RAW_STEPS) / RAW_STEPS;
    vec3 color = texture(Sampler1, cell / DEEP_EXTENT * 0.5 + 0.5).rgb;

    // Green steps are 100 per kind, see RaptureMassShape: 1 is the lip of a crack, 2 its core
    float crack = open > tear.b + RAW_SPAN ? 0.0 : tear.g * 255.0;
    if (crack > 150.0)
        color = RAW * fresh;
    else if (crack > 50.0)
        color = CRACK_LIP + RAW * fresh;
    else
        color = mix(color, RAW, fresh * fresh * 0.8);

    // On a grid of its own, turned with the camera before it is cut into cells: one rigid sprite, with the slit standing upright on screen, that no texel of changes as the player turns
    vec2 turned = vec2(BreachTurn.x * vPlane.x + BreachTurn.y * vPlane.y, BreachTurn.x * vPlane.y - BreachTurn.y * vPlane.x);
    vec2 eye = (floor(turned / CELL) + 0.5) * CELL / BreachCore.z;
    if (dot(eye, eye) < 1.0) {
        vec4 texel = texture(Sampler2, eye * 0.5 + 0.5);
        if (texel.a > 0.5 / 255.0) {
            vec3 lit = texel.a < 0.998 && texel.a < BreachCore.y ? vec3(0.008, 0.002, 0.003) : texel.rgb;
            color = mix(color, lit, BreachCore.w);
        }
    }

    fragColor = vec4(color, 1.0);
}
