---
name: effect-brief
description: Agree on how a VoidHeart visual effect should look before writing it. Use when the user asks for a new or reworked effect, animation, particle, beam, glow, hologram or other visual whose look isn't fully specified, or after feedback shows an effect reads as the wrong thing, such as "it looks like a shield". Skip it for a precise instruction or a value change like "make it fainter".
---

# Effect brief

An effect that misses the intended look costs a full build and in-game review, and words leave a lot of room. "Barrier assembling itself" first came out as growing tiles, then as an energy shield. A few lines agreed up front are much cheaper.

## When to use it

- **Write a brief** when the look is open: a new effect, a rework, or an effect that reads as the wrong thing.
- **Skip it** when the user has already decided. "Add a soft halo behind each glyph" or "the beam should disappear a bit slower" should just be done, because a brief would only add a round trip. If they've specified part of an effect, treat that part as settled and only raise what's open.

## The brief

Before writing code, read what already exists nearby, then reply with this. Keep it short enough to answer at a glance:

```markdown
**Reads as:** the one idea a player should take from it, such as "the barrier assembling itself".
**Must not read as:** the nearest wrong idea, such as "an energy shield".
**Motion:** where it starts, how it spreads, how it ends, and how long each part takes in ticks.
**Color and light:** colors, opacity, and whether it glows in the dark or takes world light.
**Prominence:** whether it's the focus or a background, and what must stay readable through it.
**Irregularity:** what varies, so it doesn't look mechanical.
**Draws on:** the closest existing effect, and what differs from it.
**Cost:** a rough idea of what's rebuilt per frame at the largest realistic size.
```

If the direction itself is open, offer two or three directions that are clearly different, not variations of one idea, each described in those terms with your recommendation first. Then wait for the user's answer before writing the effect.

## What past feedback teaches

Check each brief against these, since they're what the user has sent back before:

| Feedback | What it taught | Where |
|---|---|---|
| "more like a energetic shield" | Size and borders set what it reads as. Smaller tiles and discreet borders read as assembly | `BarrierMosaicRender`, `80d73b1` |
| Propagation points "should be randomized" and animate toward merging | Fronts need several random seeds that meet | `BarrierMosaicRender` |
| Ripples should be "less perfect and more disturbed" | Clean geometry looks artificial here | `oculus_hologram.fsh`, `b0b216a` |
| "Still too dull", twice | Things meant to glow should be emissive, with a halo if needed, and match the brightness of what's around them | `ShatterForgeRuneParticle`, `f839dd1` |
| Terrain and contours "too visible", "too bright" | Background layers should sit well below the foreground | `TravelMapScreen`, `134d1b9` |
| Lines should be fainter farther from a node | Fading ties an effect to its source | `TravelMapScreen`, `7ed42c8` |
| Motes "too visible", movement "not smooth" | Slow small dots show pixel stepping, so soften them or give them tails | `TravelMapScreen`, `90eff0f` |
| Beam should "form with an animation" and disappear "from the sources to the target forge" | The direction of motion carries the meaning: energy flows toward what uses it | `VoidBeamRender`, `faadddf` |
| Only the focused beam takes the attuned color, and collector strands carry theirs | Color marks what something is, so apply it selectively | `VoidBeamRender`, `EssenceStrandRender` |

These are lessons, not rules to apply everywhere. The user's request wins where they conflict. When new feedback teaches something else, add a row here.

## After agreement

1. **Use `voidheart-rendering`** to choose how to draw it.
2. **Build the smallest version that can be judged,** usually the riskiest part of the look, before polish and variants.
3. **Put every look value in a named constant** at the top of the class, so a note like "too bright" only needs a value change.
4. **Use `verify-in-game`** and capture the moments named in the brief, such as start, middle and end. Compare the frames against the brief's "Reads as" and "Must not read as" lines.
5. **Show the frames and say where it falls short.** Screenshots can't show how motion feels and the look is the user's call, so don't declare it right. Ask them to review the motion in the game.

## Handling feedback

Change what the feedback is about and leave the rest alone, so each review only has one thing to compare. If an effect comes back twice for the same reason, such as "still too dull", the value probably isn't the issue. Rethink the approach, as with the runes, which only landed once they rendered emissive with a halo behind each glyph.
