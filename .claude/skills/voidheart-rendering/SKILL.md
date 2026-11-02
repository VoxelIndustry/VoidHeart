---
name: voidheart-rendering
description: Render conventions and known failure modes for the VoidHeart mod on Minecraft 26.3. Use before writing or changing anything that draws, including block entity and entity renderers, particles, previews, holograms, overlays, item models, GeckoLib models and GUI screens, even for a small tweak. Also use whenever the user reports flicker, z-fighting, flashing, mirrored or misplaced visuals, wrong transparency, ignored clicks in a screen, or a render slowing the game.
---

# VoidHeart rendering

Most VoidHeart fix commits since the 26.3 port are rendering bugs that compiled and passed unit tests. This skill is for choosing correctly before coding. It doesn't prove anything works, so finish with the `verify-in-game` skill.

## Before writing code

Read the closest existing renderer below, then state three things in a few lines:

1. **Render type and why.** What it does for depth writes, blending and culling, and what happens where its geometry overlaps itself.
2. **Layering.** What else can occupy the same surface, and how they stay apart.
3. **Per-frame cost.** How many quads or GUI elements are rebuilt each frame at the worst realistic size, and what is cached.

These three decisions caused the flicker, z-fighting and slowdowns listed below, and they are much cheaper to settle here than after an in-game review.

Keep sizes, colors, alphas and timings in named constants at the top of the class, as `TravelMapScreen` does with `MOTE_ALPHA`. Feedback like "too dull" or "too visible" then only needs a value change.

## Choosing how to draw

| Need | Use | Read first |
|---|---|---|
| Additive glow that overlaps itself: ribbons, waypoints, lines | `VoidHeartRenderTypes.GLOW` | `AnchorWaypointRender`, `LeyDebugRender` |
| Colored quads that must keep their hue on bright backgrounds | `VoidHeartRenderTypes.MARKER` | `OculusMarkers`, `OculusLeyLayer` |
| Textured beams and veils | `RenderTypes.beaconBeam(texture, true)` | `VoidBeamRender` |
| Ghost or preview blocks | `RenderTypes.translucentMovingBlock()` | `ForgePreviewRender`, `PillarPlacementRender` |
| Geometry that ignores world light | `RenderTypes.entityTranslucentEmissive(...)` | `GeometryParticle`, `BarrierMosaicRender` |
| Glowing parts of a model | `RenderTypes.eyes(...)` | `BeholderRender`, `ShoulderEyeRender` |
| Large, mostly static geometry | Cached GPU meshes built off-thread | `client/render/oculus/OculusRenderer` |
| Expensive, unchanging GUI content | Bake once into a texture and blit it | `TravelMapScreen` |
| A block item that shows its GeckoLib block model | Special item model and `GeoItemRenderer` | `RavenousCollectorItemRender` |

Block entity renderers read the world in `extractRenderState` and only draw from that state when submitting. `RavenousRelayRender` is a short example. World-level drawing goes through `LevelRenderEvents`, registered in `VoidHeartClient`.

If nothing in the table fits, copy the closest vanilla pipeline as `VoidHeartRenderTypes` does, and document in a comment why it differs.

## Known failure modes

| Symptom | Cause | Fix | Commit |
|---|---|---|---|
| Glow flickers as the camera moves | Vanilla `lightning` writes depth and its quads re-sort each frame, so overlaps hide each other in a camera-dependent order | Use `GLOW`: no depth writes, order-independent additive blending | `27f73ad` |
| Geometry jitters far from the world origin | Camera-relative position subtracted in float | Subtract in double, then cast (`LeyDebugRender.relative`) | `27f73ad` |
| Z-fighting on a double-sided quad | The particle pipeline doesn't cull back faces, so a reversed copy fights the original | Draw the face once | `f839dd1` |
| Z-fighting between layers | Coplanar surfaces, such as a hologram on its table or border | Separate them by a small offset, or stop drawing the hidden one | `b0b216a` |
| Faces flash from some angles | An effect applied to faces where it can't hold steady, like scanlines on block tops | Limit the effect to the faces where it works | `b0b216a` |
| Particle or effect blinks periodically | Respawning it rerolls its look | Keep one instance alive and update it | `f839dd1` |
| Text or overlays mirrored, or turned away | Since 26.1 the camera orientation faces the other way than 1.20's entity dispatcher rotation | Rotate 180° around Y for camera-facing geometry, around X for text and sprites | `eabe2d4` |
| A placed block renders translucent | 26.3 picks the chunk layer from texture transparency | Make the block texture opaque and apply ghost alpha when drawing | `6eaa6fe` |
| GeckoLib block item sits too high | GeckoLib centers item models, but block models stand on their bottom | Override `adjustRenderPose` | `07440ce` |
| Screen slows the game | About ten thousand GUI elements rebuilt each frame | Bake into a texture | `90eff0f` |
| Clicks in a screen are ignored | 26.3 renumbered mouse buttons, so left is no longer `0` | Compare with `InputConstants.MOUSE_BUTTON_LEFT`, never a literal | `6137f55` |

Run `git show <commit>` when you need the full change. If a fix teaches something new, add a row here in the same change, so the next renderer benefits from it.

## Iris

`CraftingVoidRender` has a separate path for Iris (`IrisCompat.useIris()`), so changes there need both paths updated. Treat shader-pack behavior as unverified unless it was actually run with Iris, and say so when reporting.

## After coding

Use `verify-in-game`. A single screenshot can't show flicker, so ask for a camera sweep on anything translucent, additive or layered. Also view anything camera-facing from at least two sides.
