# Changelog

## Unreleased

- Fixed `/paste undo` duplicating mobs, dropped items, minecarts and boats (#1).
- Leaving a world or server now cancels a running paste, clears the `/paste undo` backup and stops an active scan (#2).
- `/paste undo` and a running paste now stay in the dimension the paste was made in, instead of writing into the dimension you moved to (#3).

## 1.0.0

- `/scan start <name> <radius> [minY maxY]` records the terrain around you on any server, following you as you walk.
- `/paste <name>` pastes a scan into a singleplayer world at its original coordinates; `/paste undo` reverts it.
- Copies blocks, signs and other block entities (containers come out empty), plus armor stands, item frames, paintings and display entities.
