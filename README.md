# Sim-U-Kraft Rebooted

> **Minecraft 1.7.10 only** (Forge 10.13.4.1614). There are no versions for other Minecraft releases.

An unofficial continuation of **Sim-U-Kraft Reloaded 1.0.3** for Minecraft 1.7.10. Sim-U-Folks
move into your world; you house them, hire them to build, mine, farm, bake, terraform and keep a warehouse, and watch
them make friends and families.

This is **not** the original Sim-U-Kraft or Sim-U-Kraft Reloaded, and their authors don't support it. Please report
problems here, not to them.

## Credits

- **Sim-U-Kraft**: Satscape (Scott Hather), with documentation by Subrina Gass. When he stopped developing it, Satscape
  released the source so others could "make you're own version of the mod, re-make it, use parts of it for another
  mod", asking only that new versions get a different name
  ([his page](https://scotthather.weebly.com/sim-u-kraft.html)).
- **Sim-U-Kraft Reloaded**: AshJack's 1.7.10 port, which this repository starts from: his commits are the first seven
  in the history ([his repository](https://github.com/aldentaylorAvnet/Sim-U-Kraft-Reloaded),
  [CurseForge](https://www.curseforge.com/minecraft/mc-mods/sim-u-kraft-reloaded)).
- **Sim-U-Kraft Rebooted**: yeehawitsjake.
- Skins come from Minecraft skin sites and sound effects from Soundsnap, as Satscape credited them.

The mod id is still `ashjacksimukraftreloaded`: it names every block, item and entity in a world, so changing it would
delete them from existing saves.

## What's changed from Reloaded

Among other things: the server owns all the data (no more client/server desync), folk AI and lag fixes, a pathfinder that
handles fences, walls and doors, a happiness score and a new Sim screen, apartments and hotel rooms, builders that load
the shipped blueprints and actually build, a 3D blueprint preview, a Warehouse whose keeper supplies builders (with
Applied Energistics 2 support), safety guards around mines, and many crash and save fixes. See the commit history for
details.

## Licensing

Satscape released Sim-U-Kraft's source without naming a license, and AshJack's Reloaded has no license either, so no
license is granted here for their code or for the bundled skins and sounds. `libs/ae2-api-rv3-beta-6.jar` is Applied
Energistics 2's API, used only to compile against.

## Download

Jars are on the [Releases page](https://github.com/yeehawitsjakester/SimUKraft-Rebooted/releases). Drop the jar in
`mods/`. It also needs the original Sim-U-Kraft blueprints in `.minecraft/mods/Simukraft/buildings/` (the Warehouse
blueprint is installed there automatically).

To publish a release, push a version tag: `git tag 1.1.0 && git push origin 1.1.0`. GitHub Actions builds the jar and
attaches it to a new release (`.github/workflows/release.yml`).

## Building

```
./gradlew build
```

The jar lands in `build/libs/` as `SimUKraft-Rebooted-mc1.7.10-<version>.jar`.
