#version 330
#extension GL_ARB_separate_shader_objects : require

// The view through a temple door's hole into its sanctum. The hole is flat on the mosaic; this traces each pixel's view ray into a hall behind
// it, so the hall shifts with the eye as a room would: a tunnel of drifting veil smoke, then a nave between two rows of columns, flesh spreading over its
// floor toward the back, tentacles hanging from its coffered ceiling, braziers lighting it, and at its end a painted apse where the mass waits,
// its eyes lit. Nothing of it is in the world: the sanctum lies in another dimension.
//
// Door space, in blocks: x rightward across the door from its left edge, y up from its foot, z into the wall from its face.
// Sampler0 is the hall's atlas: 16x16 tiles in its top half, the apse painted in its bottom half, whose alpha below 1 marks what glows.
// The ink mask and vertex color are as wraith_ink.fsh reads them: red how far the hole has opened, green how far the hall has come out of the
// dark, blue how brightly the hole's edge glows.

#include <minecraft:globals.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
uniform sampler2D DissolveMaskSampler;

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 6) in vec2 texCoord0;
layout(location = 7) in vec2 doorPos;
layout(location = 8) in vec3 viewPos;
layout(location = 9) in vec3 faceNormal;

layout(location = 0) out vec4 fragColor;

const float HOLE_END    = 0.8;
const float HOLE_RIM    = 0.11;
const vec3  HOLE_GLOW   = vec3(0.620, 0.300, 1.000);

const float DOOR_W      = 3.0;
const float DOOR_H      = 4.0;
// The ink tunnel through the wall, as deep as a veil's would be, then the hall beyond it, everything in it counted from the tunnel's end
const float LIP         = 4.0;
const float HALL_X0     = -4.0;
const float HALL_X1     = 7.0;
const float HALL_TOP    = 9.0;
const float HALL_BACK   = LIP + 26.2;
const float COLUMN_HALF = 0.5;
const float COLUMN_X0   = -1.9;
const float COLUMN_X1   = 4.9;
const float COLUMN_Z0   = LIP + 3.7;
const float COLUMN_STEP = 4.6;
const int   COLUMNS     = 5;
const float TENDRIL_HALF = 0.13;
const float FLAME_RADIUS = 0.16;
const float BOWL_HALF    = 0.22;
const float BOWL_TOP     = 0.75;

const vec3  INK         = vec3(0.030, 0.016, 0.042);
const vec3  INK_SHEEN   = vec3(0.110, 0.050, 0.170);
// The tunnel's smoke: how big it is on the walls, how fast it drifts in repeats per tick, how much brighter it is than the ink, how fast it
// darkens going in, and how far the hall's light spills back into it from its end
const float SMOKE_SCALE = 0.5;
const float SMOKE_DRIFT = 1.0 / 500.0;
const float SMOKE_GAIN  = 2.4;
const float TUNNEL_FALL = 0.22;
const float SPILL_REACH = 1.6;
const vec3  SPILL       = vec3(0.170, 0.110, 0.120);
const vec3  AMBIENT     = vec3(0.230, 0.210, 0.320);
const vec3  FLAME       = vec3(0.950, 0.640, 0.420);
const vec3  FLAME_CORE  = vec3(1.000, 0.880, 0.600);
const float FLAME_POWER = 1.7;
const float FLAME_REACH = 0.16;
// How fast the hall darkens with distance, and the violet haze hanging in its first blocks
const float DEPTH_FALL  = 0.028;
const vec3  HAZE        = vec3(0.180, 0.080, 0.300);
const float HAZE_REACH  = 3.0;
const vec3  EYE_GLOW    = vec3(1.000, 0.900, 0.350);
const vec3  SPECK_GLOW  = vec3(0.700, 0.250, 0.900);

// Tiles of the atlas, by their corner in 16ths of its width
const vec2  TILE_FLOOR    = vec2(0.0, 0.0);
const vec2  TILE_FLESH    = vec2(1.0, 0.0);
const vec2  TILE_BRICKS   = vec2(2.0, 0.0);
const vec2  TILE_PILLAR   = vec2(3.0, 0.0);
const vec2  TILE_CEILING  = vec2(0.0, 1.0);
const vec2  TILE_TENDRIL  = vec2(1.0, 1.0);
const vec2  TILE_BRUISED  = vec2(2.0, 1.0);
const vec2  TILE_SMOKE    = vec2(3.0, 1.0);

// Where tentacles hang and how far down, and where braziers stand
const vec3 TENDRILS[6] = vec3[](vec3(0.7, 0.0, LIP + 8.2), vec3(2.6, 0.0, LIP + 11.7), vec3(-0.6, 0.0, LIP + 14.2),
        vec3(3.4, 0.0, LIP + 16.7), vec3(1.2, 0.0, LIP + 19.7), vec3(5.9, 0.0, LIP + 12.7));
const float TENDRIL_DROP[6] = float[](2.6, 3.4, 4.1, 2.2, 4.6, 3.0);
const vec2 BRAZIERS[4] = vec2[](vec2(-0.7, LIP + 6.0), vec2(3.7, LIP + 6.0), vec2(-0.7, LIP + 15.0), vec2(3.7, LIP + 15.0));
// A dull red glow over the mass at the hall's end, so it shows against the apse
const vec3 MASS_LIGHT  = vec3(0.75, 0.22, 0.20);
const vec3 MASS_AT     = vec3(1.5, 3.0, LIP + 23.2);

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

vec4 tile(vec2 corner, vec2 uv) {
    return texture(Sampler0, (corner + fract(uv)) * 0.25);
}

// Entry and exit of a ray through a box, or a miss as (-1, -1)
vec2 box(vec3 ro, vec3 rd, vec3 lo, vec3 hi) {
    vec3 inv = 1.0 / rd;
    vec3 t0 = (lo - ro) * inv;
    vec3 t1 = (hi - ro) * inv;
    vec3 tmin = min(t0, t1);
    vec3 tmax = max(t0, t1);
    float enter = max(max(tmin.x, tmin.y), tmin.z);
    float leave = min(min(tmax.x, tmax.y), tmax.z);
    return enter < leave && leave > 0.0 ? vec2(enter, leave) : vec2(-1.0);
}

vec3 lit(vec3 albedo, vec3 p, float ticks) {
    vec3 light = AMBIENT;
    for (int i = 0; i < 4; i++) {
        vec3 flame = vec3(BRAZIERS[i].x, BOWL_TOP + 0.2, BRAZIERS[i].y);
        float d = length(p - flame);
        float flicker = 0.85 + 0.15 * sin(ticks * 0.9 + float(i) * 1.7) * sin(ticks * 0.37 + float(i));
        light += FLAME * FLAME_POWER * flicker / (1.0 + d * d * FLAME_REACH);
    }
    float m = length(p - MASS_AT);
    light += MASS_LIGHT * (0.8 + 0.2 * sin(ticks * 0.11)) / (1.0 + m * m * 0.08);
    return albedo * light;
}

void main() {
    vec4 mask = texture(DissolveMaskSampler, texCoord0);
    float holeTime = mask.r < 0.999 ? mask.r : 2.0;
    float open = vertexColor.r * HOLE_END;
    if (holeTime >= open)
        discard;
    float shown = vertexColor.g;
    float glow = vertexColor.b;
    float ticks = GameTime * 24000.0;

    vec3 n = normalize(faceNormal);
    vec3 right = vec3(n.z, 0.0, -n.x);
    vec3 view = normalize(viewPos);
    vec3 rd = vec3(dot(view, right), view.y, -dot(view, n));
    rd.z = max(rd.z, 1e-4);
    vec3 ro = vec3(doorPos, 0.0);

    vec3 color;
    // Through the ink tunnel first: whatever leaves its sides before the hall is the tunnel's wall
    float tLip = LIP / rd.z;
    vec3 atLip = ro + rd * tLip;
    if (atLip.x < 0.0 || atLip.x > DOOR_W || atLip.y < 0.0 || atLip.y > DOOR_H) {
        float tx = rd.x > 0.0 ? (DOOR_W - ro.x) / rd.x : (rd.x < 0.0 ? -ro.x / rd.x : 1e9);
        float ty = rd.y > 0.0 ? (DOOR_H - ro.y) / rd.y : (rd.y < 0.0 ? -ro.y / rd.y : 1e9);
        vec3 p = ro + rd * min(tx, ty);
        // Veil smoke drifting along the walls, two layers, darker the deeper in, warmed by the hall's light near the end
        vec2 along = vec2(p.z, p.x + p.y) * SMOKE_SCALE;
        vec3 a = tile(TILE_SMOKE, along + vec2(ticks * SMOKE_DRIFT, 0.0)).rgb;
        vec3 b = tile(TILE_SMOKE, along * 1.7 + vec2(-ticks * SMOKE_DRIFT * 1.3, ticks * SMOKE_DRIFT)).rgb;
        color = mix(INK, a * (0.6 + SMOKE_GAIN * b), 0.8) * exp(-p.z * TUNNEL_FALL);
        color += SPILL * smoothstep(LIP - SPILL_REACH, LIP, p.z) * shown;
    } else {
        // The hall's shell: floor, ceiling, sides and back
        vec3 o = atLip;
        float tx = rd.x > 0.0 ? (HALL_X1 - o.x) / rd.x : (rd.x < 0.0 ? (HALL_X0 - o.x) / rd.x : 1e9);
        float ty = rd.y > 0.0 ? (HALL_TOP - o.y) / rd.y : (rd.y < 0.0 ? -o.y / rd.y : 1e9);
        float tz = (HALL_BACK - o.z) / rd.z;
        float t = min(min(tx, ty), tz);
        int surface = t == tz ? 0 : t == ty ? (rd.y > 0.0 ? 1 : 2) : 3;
        vec3 normalHit = vec3(0.0);

        // Columns, tentacles and brazier bowls in front of it
        for (int side = 0; side < 2; side++) {
            float cx = side == 0 ? COLUMN_X0 : COLUMN_X1;
            for (int i = 0; i < COLUMNS; i++) {
                float cz = COLUMN_Z0 + float(i) * COLUMN_STEP;
                vec2 hit = box(o, rd, vec3(cx - COLUMN_HALF, 0.0, cz - COLUMN_HALF), vec3(cx + COLUMN_HALF, HALL_TOP, cz + COLUMN_HALF));
                if (hit.x > 0.0 && hit.x < t) {
                    t = hit.x;
                    surface = 4;
                }
            }
        }
        for (int i = 0; i < 6; i++) {
            vec3 c = TENDRILS[i];
            vec2 hit = box(o, rd, vec3(c.x - TENDRIL_HALF, HALL_TOP - TENDRIL_DROP[i], c.z - TENDRIL_HALF), vec3(c.x + TENDRIL_HALF, HALL_TOP, c.z + TENDRIL_HALF));
            if (hit.x > 0.0 && hit.x < t) {
                t = hit.x;
                surface = 5;
            }
        }
        for (int i = 0; i < 4; i++) {
            vec2 b = BRAZIERS[i];
            vec2 hit = box(o, rd, vec3(b.x - BOWL_HALF, 0.0, b.y - BOWL_HALF), vec3(b.x + BOWL_HALF, BOWL_TOP, b.y + BOWL_HALF));
            if (hit.x > 0.0 && hit.x < t) {
                t = hit.x;
                surface = 6;
            }
        }

        vec3 p = o + rd * t;
        vec3 albedo;
        vec3 emission = vec3(0.0);
        if (surface == 0) {
            // The apse, painted: its alpha marks the eyes and the specks of the writhing stone, which glow
            vec2 uv = vec2((p.x - HALL_X0) / (HALL_X1 - HALL_X0), 1.0 - p.y / HALL_TOP);
            vec4 painted = texture(Sampler0, vec2(uv.x, 0.5 + uv.y * 0.5));
            albedo = painted.rgb;
            float glowing = 1.0 - painted.a;
            float blink = step(0.06, fract(ticks / 97.0 + floor(uv.x * 8.0) * 0.37));
            emission = painted.rgb * (glowing > 0.5 ? EYE_GLOW * blink * 2.2 : SPECK_GLOW * glowing * 2.0);
        } else if (surface == 1) {
            albedo = tile(TILE_CEILING, p.xz).rgb;
        } else if (surface == 2) {
            // Stone near the door, flesh taking over toward the back, block by block
            vec2 cell = floor(p.xz);
            float flesh = smoothstep(LIP + 1.2, LIP + 14.2, p.z) * 0.95;
            bool fleshy = hash(cell) < flesh;
            albedo = tile(fleshy ? (hash(cell + 7.0) < 0.3 ? TILE_BRUISED : TILE_FLESH) : TILE_FLOOR, p.xz).rgb;
        } else if (surface == 3) {
            albedo = tile(TILE_BRICKS, vec2(p.z, p.y)).rgb;
        } else if (surface == 4) {
            albedo = tile(TILE_PILLAR, vec2(p.x + p.z, p.y)).rgb;
        } else if (surface == 5) {
            albedo = tile(TILE_TENDRIL, vec2(p.x + p.z, p.y * 1.5)).rgb;
        } else {
            albedo = tile(TILE_PILLAR, vec2(p.x + p.z, p.y)).rgb * 0.5;
        }
        color = lit(albedo, p, ticks);

        // Flames over the bowls, nearer than whatever they stand before
        for (int i = 0; i < 4; i++) {
            vec3 flame = vec3(BRAZIERS[i].x, BOWL_TOP + 0.2, BRAZIERS[i].y);
            vec3 toFlame = flame - o;
            float along = dot(toFlame, rd);
            if (along > 0.0 && along < t) {
                float miss = length(toFlame - rd * along);
                float size = FLAME_RADIUS * (0.9 + 0.2 * sin(ticks * 1.3 + float(i) * 2.0));
                if (miss < size)
                    emission += mix(FLAME_CORE, FLAME, miss / size) * 1.4;
            }
        }

        float distance = tLip + t;
        color = color * exp(-distance * DEPTH_FALL) + emission;
        color = mix(color, HAZE, clamp(1.0 - t / HAZE_REACH, 0.0, 1.0) * 0.35);
    }

    color *= shown;
    // The hole's edge smoulders as a veil's does
    if (holeTime > open - HOLE_RIM)
        color = mix(color, HOLE_GLOW, (1.0 - (open - holeTime) / HOLE_RIM) * glow);

    fragColor = apply_fog(vec4(color, 1.0), sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd,
            FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
