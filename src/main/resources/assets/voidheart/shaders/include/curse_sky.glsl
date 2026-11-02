#ifndef VOIDHEART_CURSE_SKY_GLSL
#define VOIDHEART_CURSE_SKY_GLSL

layout(std140) uniform CurseSky {
    // From a sky staying still around the player, +y straight up, to view space
    mat4 SkyView;
    // From the star field, turning with the time of day, to view space
    mat4 StarView;
    // x: seconds since the curse began, y: how bright vanilla would draw the stars
    vec4 CurseTime;
    // State of the curse, whose meaning is up to its shaders
    vec4 CurseStateA;
    vec4 CurseStateB;
};

#endif
