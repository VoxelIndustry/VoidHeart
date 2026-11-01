---
name: verify-in-game
description: Run the VoidHeart mod in a real Minecraft client through Fabric client game tests, look at the screenshots, and report what was actually observed. Use after any change to gameplay, rendering, input, screens, block behavior, redstone, saved state or recipes, before calling the work done, and whenever the user reports an in-game bug such as flicker, z-fighting, a wrong look or something that does nothing. Compiling and passing unit tests is not enough for these changes, so use this even if the user doesn't ask for testing.
---

# Verify in game

Code in this mod regularly compiles and passes unit tests, then fails in play. Client game tests start a real client and world, so Claude can check much of this itself instead of leaving every bug for the user's review. The goal is to catch what can be caught here, and to say clearly what can't.

Don't ask the user to accept the EULA or launch anything: `configureTests` in `build.gradle` already sets `eula = true`, and the test opens its own game window.

## 1. Decide what must be proven

List the behaviors the change affects, each as something a test could observe. For example: "the forge won't start with its output slot full", or "the veil stays steady while the camera moves".

For a reported bug, reproduce it first when possible. A test that fails before the fix and passes after it is the strongest evidence you can give.

## 2. Add or extend a client game test

Tests live in `src/gametest/java/net/voxelindustry/voidheart/gametest/`. Extend the feature's existing test, or add a class and **list it in `src/gametest/resources/fabric.mod.json`**, because an unlisted test silently never runs. Keep tests when they're useful, so coverage grows with each feature.

Good templates:

| Need | Read |
|---|---|
| Assertions on server and client state, third-person screenshots | `ShoulderEyeClientTest` |
| Real clicks and aiming at a point | `OculusControlsClientTest` (`aimFrom`, `context.getInput()`) |
| Generated terrain and frame cost numbers | `OculusPerformanceClientTest` |
| Screenshots from several angles and zooms | `OculusTableClientTest` |

The usual setup:

```java
try (var world = context.worldBuilder().create())
{
    var server = world.getServer();
    world.getConnection().waitForChunksRender();
    server.runCommand("time set noon");
    server.runCommand("gamerule advance_time false");
    server.runCommand("gamemode creative @a");
    context.waitTicks(20);
    context.runOnClient(minecraft -> minecraft.options.setCameraType(CameraType.FIRST_PERSON));
    // F1 hides the HUD, hand, chat and toasts, press it again before the test ends
    context.getInput().pressKey(InputConstants.KEY_F1);
    // Build the scene with commands, aim with tp, then assert and capture
}
```

These details have spoiled scenes before:

- **All tests share one client.** Settings like the camera type carry over from earlier tests, so a scene can look right alone and wrong in the full suite. Set what the scene needs and restore what you change.
- **Landing ends creative flight.** Set `getAbilities().flying` and call `onUpdateAbilities()` after each `tp` into the air, or the player falls out of frame. Spectator mode avoids this when the test doesn't need clicks.
- **`server.runCommand` runs as the server, not the player.** Use `execute as @a run ...` for commands that need a player.

Use real input where the bug could be in the input path. Setting state directly would have missed the travel map's ignored clicks.

### Assert before you look

Throw `AssertionError` for anything checkable through `server.computeOnServer` or `context.computeOnClient`: inventory contents, block state, saved settings after a merge or reload, redstone response. Assertions don't depend on interpreting an image, and they keep guarding the behavior later.

### Choose captures that can show the failure

- **Layered, translucent or additive effects:** a single frame can't show flicker. Freeze the game so animation holds still, then sweep the camera in small steps close to the effect. Keep `sweep` in the screenshot names, since `framediff.py` looks for it.

  ```java
  server.runCommand("tick freeze");
  for (int step = 0; step < 8; step++)
  {
      // aimFrom as in ShatterForgeClientTest, from a few blocks away
      aimFrom(context, server, x + step * 0.05, y, z + step * 0.08, targetX, targetY, targetZ, 2);
      context.takeScreenshot("forge-beam-sweep-" + step);
  }
  server.runCommand("tick unfreeze");
  ```

  This does catch real flicker. With the render type from before fix `27f73ad`, ley line cores broke into bands and gaps that changed between frames. With the fix they stayed solid.

- **Coplanar surfaces:** capture a grazing angle as well as a straight-on one, since z-fighting tends to be worst there.
- **Camera-facing geometry:** capture from two opposite sides.
- **Animations:** capture named moments such as start, middle and end, using `waitTicks` or a state wait like `waitForBeam` in `ShoulderEyeClientTest`.
- **Items:** capture them in hand and in the inventory as well as the placed block.
- **Performance risks:** log frame cost at the largest realistic size, as `OculusPerformanceClientTest` does.

## 3. Run it

Send the output to a file in the scratchpad, since the log is long:

```bash
./gradlew runClientGameTest --console=plain > "<scratchpad>/gametest.log" 2>&1
```

Run it in the background, because the full suite takes about 4 minutes. While iterating, add `-PgameTest=ShatterForge` to run only the tests whose class name contains that text, which takes under a minute. Do one full run without it before reporting, so regressions and leftover client settings show up. Don't edit `fabric.mod.json` to narrow a run.

Other sessions may share this checkout and its `build/` folder, and their runs can wipe your screenshots. Check with `git worktree list` and your session list, and do repeated runs and temporary source changes in your own worktree, which has its own `build/`.

## 4. Check the results

1. **Log:** search for `AssertionError`, `Exception` and `BUILD`.
2. **Screenshots:** read the PNGs with the Read tool, from `build/run/clientGameTest/screenshots/`. They are named `NNNN_<name>.png` at 854x480, and the number shifts when tests are added, so find them by name. If a detail is too small to judge at that size, move the camera closer, capture with `TestScreenshotOptions.of(name).withSize(width, height)`, or enlarge the region with `crop.lua` from the `aseprite-texture` skill.
3. **Sweeps:** look at several frames, then measure them. Pass one or more folders to get the count of strongly changed pixels per frame pair:

   ```bash
   python .claude/skills/verify-in-game/scripts/framediff.py "<scratchpad>/before" "<scratchpad>/after"
   ```

   Moving the camera always changes pixels, so the signal is uneven counts, not high ones. The flickering ley lines gave 7,319 to 12,453 and the fixed ones 6,411 to 6,904. Counts depend on the scene, so compare within a scene, ideally before and after a change, and treat the number as a pointer to which frames to look at. Detailed textures close to the camera dominate the counts: a forge sweep with monoliths in the foreground gave 56,067 to 62,716 at 1280x720, which says little. For a useful measure, frame the effect against open sky or ground. Run the script on sweep frames only.
4. **Compare runs:** `clearRunDirectory = true` wipes that folder on each run. Before re-running, copy any screenshots you want as a "before" into the scratchpad.

Actually look at each relevant image and describe what you see. Don't conclude from the fact that the test passed: a screenshot of an empty scene passes too. If the scene is wrong, such as the camera facing away, fix the test and run it again before judging the feature.

## 5. Report honestly

Use these groups in the final message:

- **Asserted:** behaviors a test checked, and that passed or failed.
- **Seen:** what the screenshots show, including the angles and moments covered.
- **Not verified:** what wasn't run or can't be judged here, plus any problems you suspect.
- **For your review:** a short checklist of what still needs a person, each item with what to do and what to expect. Include motion feel, smoothness, real frame rate and Iris shaders when they apply.

Don't describe something as working if it wasn't observed. A static screenshot supports "looks right from this angle" and nothing wider. Whether an effect has the right feel is the user's call, so show the evidence and ask rather than declaring it good.

CI runs `./gradlew build`, which doesn't include `runClientGameTest`, and it won't: these tests aren't practical on headless runners and would be too expensive there. Green CI says nothing about them, so always run them locally, and don't propose adding them to CI.
