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

`-PgameTest` runs only the client game tests whose class name contains that text. The full suite takes about 4 minutes.

## Layout

- `src/main/java/net/voxelindustry/voidheart/`: `common/` for shared code and `common/setup/` for registration, `client/` for rendering and screens, `compat/` for Jade, REI, Iris and Mod Menu, `data/` for datagen providers.
- `src/main/generated/`: datagen output, committed. Change the providers and rerun `runDatagen` rather than editing these files.
- `src/test/`: unit tests for pure logic.
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
