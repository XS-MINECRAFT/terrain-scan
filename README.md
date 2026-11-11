# Terrain Scan

Client-side Fabric mod for Minecraft 26.3: scan terrain on any server (the mod is not needed there), then paste it into a singleplayer world at its original coordinates.

## Commands

| Command | What it does |
|---|---|
| `/scan start <name> <radius> [minY maxY]` | Records every chunk within `radius` chunks of you, following you as you walk. Green outlines are scanned chunks, red are pending. |
| `/scan stop` | Stops recording. The scan is saved in `.minecraft/terrainscan/<name>/`. |
| `/paste <name>` | Singleplayer only. Pastes the scan at its original coordinates and teleports you to where the scan started. |
| `/paste undo` | Reverts the last paste (kept in memory until the game restarts). |

Blocks, block entities (without container contents) and decorative entities (armor stands, item frames, paintings, displays) are copied. Mobs and items are not.
Singleplayer worlds start frozen so pasted water, lava and sand stay put; `/tick unfreeze` resumes them.

## Building

Requires JDK 25.

```
./gradlew build                # jar in build/libs/
./gradlew runClient            # dev client
./gradlew runClientGameTest    # end-to-end test: dedicated server scan, singleplayer paste, undo
./gradlew publishMods          # dry run without MODRINTH_TOKEN: payloads in build/publishMods/
```

## Releasing

1. Bump `mod_version` in `gradle.properties` and add a `## <version>` section at the top of `CHANGELOG.md`.
2. Commit, then tag and push: `git tag v1.0.0+26.3 && git push origin v1.0.0+26.3`.
3. The `release` workflow publishes the jar to Modrinth, CurseForge (if `curseforge_id` is set) and GitHub Releases.
   It needs the repo secrets `MODRINTH_TOKEN` (and `CURSEFORGE_TOKEN`).
