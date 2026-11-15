#version 330
#extension GL_ARB_separate_shader_objects : require

// The second heart taking its player apart and putting them back. Vanilla's entity cutout, with a front that runs across the body from an
// origin: the wound as it dissolves, the chest as it reforms. Behind the front the body is gone; on it burns a thin rim, white-hot fading to
// violet, that ignores world light. The front's place at each point mixes the distance from the origin with blocky noise fixed to the body, in
// texel-sized steps, so it breaks up like the model's own pixels.
//
// TextureMat carries what changes per draw, since a model's vertices can't: column 0 the origin and how far the front has run, column 1 the
// body's anchor and how many units make a block, column 2 the seed and whether it is reforming.

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexPerFaceColorBack;
layout(location = 3) in vec4 vertexPerFaceColorFront;
layout(location = 4) in vec4 lightMapColor;
layout(location = 5) in vec4 overlayColor;
layout(location = 6) in vec2 texCoord0;
layout(location = 7) in vec3 bodyPos;

layout(location = 0) out vec4 fragColor;

const vec3  RIM_HOT    = vec3(1.000, 0.930, 1.000);
const vec3  RIM_COOL   = vec3(0.560, 0.200, 0.950);
// Width of the rim in front units, and the white-hot part of it
const float RIM_WIDTH  = 0.075;
const float HOT_WIDTH  = 0.025;
// The front's place: this much from distance, this much from noise
const float DIST_SHARE  = 0.72;
const float NOISE_SHARE = 0.28;
// Distance from the origin, in blocks, that counts as the far end of the body
const float REACH       = 2.0;
// Noise cells per block, and texels per block for the steps
const float NOISE_SCALE = 3.0;
const float STEPS       = 16.0;

float hash(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), f.x),
                   mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), f.x),
                   mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

void main() {
    vec3 origin = TextureMat[0].xyz;
    float front = TextureMat[0].w;
    vec3 anchor = TextureMat[1].xyz;
    float unitsPerBlock = max(TextureMat[1].w, 1.0e-4);
    float seed = TextureMat[2].x;
    bool reform = TextureMat[2].y > 0.5;

    // In blocks from the anchor, snapped to the model's texels
    vec3 local = floor((bodyPos - anchor) / unitsPerBlock * STEPS + 0.5) / STEPS;
    vec3 fromOrigin = local - (origin - anchor) / unitsPerBlock;
    float n = noise(local * NOISE_SCALE + seed) * 0.65 + noise(local * NOISE_SCALE * 2.3 - seed) * 0.35;
    float at = clamp(length(fromOrigin) / REACH, 0.0, 1.0) * DIST_SHARE + n * NOISE_SHARE;

    // How far behind the front this point is, positive where the body is still whole
    float ahead = reform ? front - at : at - front;
    if (ahead < 0.0)
        discard;

    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.1)
        discard;

    vec4 faceColor = gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack;
    color *= faceColor * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;

    if (ahead < RIM_WIDTH && front > 0.0 && front < 1.0 + RIM_WIDTH) {
        vec3 rim = mix(RIM_HOT, RIM_COOL, smoothstep(HOT_WIDTH, RIM_WIDTH, ahead));
        color.rgb = mix(rim, color.rgb, smoothstep(RIM_WIDTH * 0.6, RIM_WIDTH, ahead));
    }

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd,
            FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
