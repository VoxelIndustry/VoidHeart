#ifndef VOIDHEART_OCULUS_GLSL
#define VOIDHEART_OCULUS_GLSL

// Hologram space is in table blocks: origin at the table footprint corner, y up from the table top
layout(std140) uniform OculusProjection {
    mat4 HoloModelView;
    // Footprint width and depth in table blocks, table blocks per world block, window height in table blocks
    vec4 HoloGeometry;
    // x: how much of the hologram is there, sinking into the void while the table sleeps
    // y: from 0 showing the surface of the land to 1 showing the hollows inside it, both at 0.5
    vec4 HoloState;
};

// Relief voxels are written half transparent, every block color fully opaque
const float RELIEF_ALPHA_LIMIT = 0.75;
// Set in the face byte on faces turned toward the open space under cover
const uint FACES_HOLLOW = 8u;

layout(std140) uniform OculusChunk {
    // Origin of the drawn chunk slice relative to the projected view center, in world blocks
    vec4 ChunkOffset;
};

#endif
