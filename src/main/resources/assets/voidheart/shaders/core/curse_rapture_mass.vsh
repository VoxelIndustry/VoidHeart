#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:projection.glsl>
#include <voidheart:curse_sky.glsl>
#include <voidheart:curse_rapture_mass_common.glsl>

// One cube of the mass around the breach. Comes out of the tear in stages, heaves very slowly, then withdraws fast.
// Its depth is squeezed: depth is SQUEEZE over the distance, so it stays right among cubes and all of it sits behind anything in the world.
// A depth range of -1 to 1 can't tell values this close to the far end apart, it gets a wider squeeze to stay right among cubes at the risk of what is farther than most of the render distance.

layout(location = 0) in vec3 Position;
layout(location = 1) in vec3 Offset;
layout(location = 2) in vec3 Origin;
layout(location = 3) in vec2 UV0;
layout(location = 4) in vec4 Color;
layout(location = 5) in vec4 Params;

layout(location = 0) out vec2 vUv;
layout(location = 1) out vec3 vColor;

const float SQUEEZE = 4.0e-6;
const float SQUEEZE_WIDER = 2.0e-3;

const float STAGES = 5.0;
// Reversed, the last ones out going back first, over this share of the slam
const float SNAP_SPREAD = 0.45;

const float HEAVE = 1.15;
const float HEAVE_RATE = 0.52;
const vec3 HEAVE_ACROSS = vec3(0.071, 0.043, 0.059);
const float SIDLE = 0.8;
const float SIDLE_RATE = 0.37;
const vec3 SIDLE_ACROSS = vec3(-0.051, 0.067, 0.083);
const float SWELL = 0.055;
const float SWELL_RATE = 0.71;
const vec3 SWELL_ACROSS = vec3(0.113, -0.037, 0.097);

void main() {
    float threshold = Params.x;
    float span = Params.y;
    float writhe = Params.z;
    float phase = Params.w * 6.2832;

    float emerged = clamp((BreachMotion.x - threshold) / span, 0.0, 1.0);
    emerged = floor(emerged * STAGES + 0.5) / STAGES;
    float back = clamp(BreachMotion.y * (1.0 + SNAP_SPREAD) - (1.0 - threshold) * SNAP_SPREAD, 0.0, 1.0);
    emerged *= 1.0 - back;

    float eased = emerged * (2.0 - emerged);
    vec3 center = mix(Origin, Position, eased);
    center.xz *= 1.0 + BreachMotion.z;

    float time = BreachMotion.w;
    vec3 outward = normalize(Position);
    vec3 across = normalize(cross(outward, vec3(0.0, 1.0, 0.0)) + vec3(0.0, 0.001, 0.0));
    float heave = sin(time * HEAVE_RATE + dot(Position, HEAVE_ACROSS) + phase * 0.25);
    float sidle = sin(time * SIDLE_RATE + dot(Position, SIDLE_ACROSS));
    center += (outward * heave * HEAVE + across * sidle * SIDLE) * writhe * eased;

    float swell = 1.0 + SWELL * writhe * sin(time * SWELL_RATE + dot(Position, SWELL_ACROSS) + phase * 0.5);
    vec3 world = center + Offset * eased * swell;

    vUv = UV0;
    vColor = Color.rgb;
    gl_Position = ProjMat * SkyView * vec4(world, 1.0);
#ifdef RENDERPEARL_DEPTH_IS_ZERO_TO_ONE
    gl_Position.z = SQUEEZE;
#else
    gl_Position.z = 2.0 * SQUEEZE_WIDER - gl_Position.w;
#endif
}
