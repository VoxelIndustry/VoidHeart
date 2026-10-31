#ifndef VOIDHEART_OCULUS_GLSL
#define VOIDHEART_OCULUS_GLSL

// Hologram space is in table blocks: origin at the table footprint corner, y up from the table top
layout(std140) uniform OculusProjection {
    mat4 HoloModelView;
    // Footprint width and depth in table blocks, table blocks per world block, window height in table blocks
    vec4 HoloGeometry;
};

layout(std140) uniform OculusChunk {
    // Origin of the drawn chunk slice relative to the projected view center, in world blocks
    vec4 ChunkOffset;
};

#endif
