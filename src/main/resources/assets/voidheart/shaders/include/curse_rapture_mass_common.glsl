#ifndef VOIDHEART_CURSE_RAPTURE_MASS_COMMON_GLSL
#define VOIDHEART_CURSE_RAPTURE_MASS_COMMON_GLSL

layout(std140) uniform CurseBreach {
    // x: how far it has opened, 0 to 1, y: how far it has slammed shut, 0 to 1, z: how much wider it got since, w: seconds in steps
    vec4 BreachMotion;
    // x: how far the sky is torn right now, y: size of the pupil, z: tangent of the radius of the eye, w: how much of it has come out of the dark
    vec4 BreachCore;
    // x, y: cosine and sine of the camera's yaw
    vec4 BreachTurn;
};

#endif
