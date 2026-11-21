# VoidHeart

A Fabric mod for Minecraft 26.3 on Java 25, using Mojang mappings. It depends on SteamLayer, from a private Maven that needs `gitea_token`, plus GeckoLib, YACL, REI and Jade, and has Iris compatibility code.

## Commands

```bash
./gradlew compileJava --console=plain
./gradlew test --console=plain
./gradlew runClientGameTest --console=plain
./gradlew runClientGameTest -PgameTest=ShatterForge --console=plain
./gradlew runDatagen --console=plain
```

`-PgameTest` runs only the client game tests whose class name contains that text, and takes a comma-separated list. The tests run on a virtual clock, about ten times faster than real time, so the full suite takes about 4 minutes. `-PgameTestRealtime` runs them at real speed, which takes over 30 minutes for the full suite.

## Layout

- `src/main/java/net/voxelindustry/voidheart/`: `common/` for shared code and `common/setup/` for registration, `client/` for rendering and screens, `compat/` for Jade, REI, Iris and Mod Menu, `data/` for datagen providers.
- `src/main/generated/`: datagen output, committed. Change the providers and rerun `runDatagen` rather than editing these files.
- `src/test/`: unit tests for pure logic.
- `src/main/java/.../common/world/dungeon/`: void dungeons. `DungeonPlan` is pure and unit tested; `DUNGEON_PREVIEW=true ./gradlew test --tests '*DungeonPlanPreview*'` draws cuts through them into `build/dungeon-preview` without starting a game.
- `src/main/java/.../common/content/armillary/`: the way into dungeons. `ArmillaryChart` and `ArmillaryRoots` are pure and unit tested; `DUNGEON_PREVIEW=true ./gradlew test --tests '*ArmillaryRootsPreview*'` draws the roots into `build/armillary-preview`. `/voidheart armillary reveal|search <percent>|decay <seconds>` skips its timers for review.
- `src/main/java/.../common/world/daedalus/`: the Daedalus, an infinite maze of districts. Plans and painting are pure and unit tested, and content may only depend on the seed and a district's position. `DUNGEON_PREVIEW=true ./gradlew test --tests '*DaedalusPreview*'` draws it into `build/daedalus-preview`. `/voidheart daedalus enter|leave|where|visit <theme>` reaches it for review.
- `src/main/java/.../common/world/underway/`: the Underway, forts and rail lines under the Daedalus districts, written into the dimension's lowest sections. Plans, furnishing and painting are pure and unit tested; `DUNGEON_PREVIEW=true ./gradlew test --tests '*UnderwayPreview*'` draws them into `build/underway-preview`. Props are written once as boxes in `UnderwayProp`, which feeds both their datagen models and their shapes. `/voidheart underway enter|hatch [station|hub|post]` and `where` reach it for review: `enter` goes to the foot of a fort's hatch stairs, `hatch` onto the hatch in the district above. About a fort in three has a temple bay (`UnderwayTemple`); `/voidheart underway temple [ruin]` goes to the doorway of the nearest one. Its mosaic is drawn by `.claude/tools/temple_mosaic.lua` and cut into the 120 tiles of `underway_mosaic`. Stared at, the mosaic wakes on that player's client alone (`client/render/temple/`, pure parts unit tested in `MosaicWakeTest`); run the script with `--script-param "wake=src/main/resources/assets/voidheart"` after changing the picture, so the texture, map and features it moves by stay in step with the tiles.
- `src/main/java/.../common/world/sanctum/` and `common/content/sanctum/`: the sanctum behind a temple's mosaic. A Wraith Veil pressed to the mosaic's door (`MosaicDoor`) opens a `VeilGate` into a sanctum in a row of its own west of the dungeons, one per temple, kept in `SanctumState`. `SanctumPlan` is pure and unit tested; `DUNGEON_PREVIEW=true ./gradlew test --tests '*SanctumPreview*'` draws it into `build/sanctum-preview`. Its fight's numbers are in `SanctumBalance`, and the pillar's contraption comes from the `voidheart:blessed_contraptions` item tag. `/voidheart sanctum enter|leave|door|slay|reset` reaches it for review.
- `src/main/java/.../common/content/underway/`: what guards the Underway. A fort's three levels are sections, each with a breaker cabinet; `UnderwaySecurity` saves what happened to each and `SectionState` is its pure, unit tested logic. The fort plans its units (`UnderwayUnit`), `security/UnderwayUnitSpawner` posts them once at generation, and their numbers are in `SecurityBalance`. `/voidheart underway section [live|dark|alarm|reset]` sets the section the player stands in.
- `src/main/java/.../common/content/hoard/`: the Hoard, items kept per pocket owner as saved data (`PocketHoard`), given room by Hoard Coffers inside the pocket and reached through Folds anywhere. `/voidheart hoard list|fill|capacity|clear` works on the caller's hoard.
- `src/main/java/.../common/content/works/`: Officiants working an altar, a forge's source pillar or a crafting table from the hoard, and the orders they take. `WorksPlan`, `WorksLayout` and `WorksQueue` are pure and unit tested; `DUNGEON_PREVIEW=true ./gradlew test --tests '*WorksPreview*'` draws plans for real items into `build/works-preview`. `/voidheart works order <item> <count>|jobs|cancel <id>` orders for review. A Memoriam Lens set in an Oculus Table makes it a Reckoning, whose screen is the hoard view with a works tab (`client/screen/WorksView`) fed by a `WorksSession` per open menu; an order always crafts its full count, and stays queued until its last claimed craft is finished.
- `src/main/java/.../client/screen/ResearchMapPlan.java`: places every research and constellation name on the research map from the tree alone, so research JSON has no position. `ResearchMapSettle` spreads the researches evenly without letting branches cross, and `ResearchMapRoutes` draws the branches as curves. The map keeps one size on every screen: small screens pan it, large ones leave dark around it. It is pure and unit tested; `DUNGEON_PREVIEW=true ./gradlew test --tests '*ResearchMapPreview*'` draws it into `build/research-preview`.
- `src/main/java/.../common/content/crystal/`: crystallography. The Crystal Cradle regrows shards into a bundle of the same worth on ley, and what fed the growth sets the bundle's habit, kept as the `habit` block state on placed bundles and the `crystal_habit` component on items. `CradleGrowth` is pure and unit tested. A grown bundle breaks into exactly the shards that grew it, never what fortune makes of a world bundle, so regrowing can't make crystal; anything that turns a placed bundle back into an item must keep its habit (`CrystalBundles.itemOf`). `/voidheart crystal grow [habit]` finishes the cradles near the player for review. Fouled crystal only loses its foulness by being scoured, which pushes taint into the stone's upstream node (`CrystalCradleTile.feed`); a soaked Scoured rubrum keeps what it drew in on its item, so cradles and sockets refuse it. A relay's Fiery, Stillness or Suffering teaches Searing, Still or Wrung (`CradleEssence`), and the cradle only asks for essence that can settle. Rubrum Cells take their bundle's habit through `HabitShapedRecipe`, and `CellHabits` says what each does in a socket. Void energy grows Deep in the pockets, dungeons and Daedalus. A Deep memoriam records the land around its cradle into `CrystalRecordings` (the overworld's saved data) when it finishes, and an Oculus Table it is set on shows that place through `OculusMemories` instead of its own land. A Deep pylon's field reaches on through the open portal-frame pairs it holds (`DeepReach`). Every habit's shard, bundle and cell textures are drawn from the plain ones by `.claude/tools/crystal_habits.lua`, so rerun it after changing a plain crystal or cell texture.
- `src/gametest/`: client game tests. Each one must be listed in `src/gametest/resources/fabric.mod.json`, or it silently never runs.

## Skills

Use these when they apply:

| Skill | Use it for |
|---|---|
| `verify-in-game` | Checking gameplay, rendering, input or saved state changes in a real client |
| `voidheart-rendering` | Anything that draws, or a visual bug |
| `new-voidheart-content` | New blocks, items, entities, recipes or machines |
| `effect-brief` | Agreeing how an effect should look when that's still open |
| `aseprite-texture` | Any image work, since Pillow isn't installed |

## Looking up APIs

Don't rely on memory for Minecraft, Fabric or mod APIs, because 26.3 has renamed and renumbered a lot. `.claude/tools/api.py` searches the exact versions this build uses:

```bash
python .claude/tools/api.py classes RenderTypes
python .claude/tools/api.py grep "submitCustomGeometry\(" --only minecraft
python .claude/tools/api.py show net.minecraft.client.gui.Hud --lines 200-240
python .claude/tools/api.py sig net.minecraft.client.Options --match hud
```

If `python .claude/tools/api.py status` reports no Minecraft sources, run `./gradlew genSources --console=plain`, which takes under a minute. For questions spanning several classes, use the `mc-api-lookup` agent so the search stays out of the main conversation.

## Rules

- Passing compilation and unit tests doesn't show that gameplay or rendering works. Report what was actually observed and what wasn't.
- Client game tests run locally only. CI runs `./gradlew build`, and they won't be added to it, because headless runners can't run them and they'd be too expensive.
- Commit or push only when asked. Commit messages look like `feat: Add void beams to the Shatter Forge`, using `feat`, `fix`, `perf`, `refactor`, `test`, `ci` or `chore`.
- Altar and Shatter Forge recipes go through datagen in `RecipesProvider`, not hand-written JSON.
- Use `InputConstants` for mouse buttons and keys. 26.3 renumbered them, so literal numbers break.
- Keep `en_us.json` line endings as they are, and check `git diff --stat` after editing it.
- Other sessions may share this checkout. Do temporary source changes and repeated test runs in your own worktree.

## Hooks

`.claude/settings.json` runs two project hooks. `java_guard.py` flags Java edits that bring back a known bug, and new entries go in its `KNOWN_BUGS` list. `verify_reminder.py` asks once per turn for a game test when `src/main` changed.
