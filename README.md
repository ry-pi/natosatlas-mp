> **Unofficial multiplayer fork (1.5.4-mp) of [Natos Atlas](https://github.com/natowb/bm-natosatlas) by natowb, MIT licensed (see `LICENSE`, unchanged).**
> This fork is based on upstream tag `1.5.4` and adds multiplayer fixes, a True colour block mode, Classic grass in True colour mode, server region-file import, a manual slime seed and a corrected slime-chunk formula. See the change notes at the bottom.
> Only the **StationAPI** build was patched and tested (Babric, StationAPI 2.0.0-alpha.6.4 at runtime). The ModLoader/BTA modules share the core code but were not tested.
> Fork builds are on this repo's Releases page; the upstream releases link below is the original mod.

<p align="center">
  <img src="assets/banner.png" alt="Natos Atlas Banner">
</p>

A world map mod for **Minecraft Beta 1.7.3**, currently available for **StAPI**,  **ModLoader**, and **BTA**.

## 🚧 Early Development Notice
This project is in a very early stage. Designs are experimental, features will change frequently, and instability is expected while work progresses toward version 1.0.

## Requirements

**ModLoader Requirements:**
* [Mod Loader b1.7.3](<https://mcarchive.net/mods/modloader>)

**StAPI Requirements:**
* [Babric Prism/MultiMC Instance](<https://github.com/babric/prism-instance>)
* [StationAPI >=2.0.0-alpha.3](<https://modrinth.com/mod/stationapi>)

**BTA Requirements**
* [BTA Fabric Loader](<https://github.com/Turnip-Labs/fabric-loader/releases>)
* [Halplibe >= 5.3.3](<https://github.com/Turnip-Labs/bta-halplibe/releases/tag/v5.3.3>)

## 📦 Installation
If you already have a Minecraft Beta 1.7.3 instance set up with either **ModLoader** or **StAPI**,
simply download the latest build from the [Releases page](https://github.com/natowb/bm-natosatlas/releases) and place the file into your instance’s `mods` folder.

## 🖼️ Preview

### Map Modes
<p align="center">
  <img src="/assets/modeDay.png" width="30%" alt="Day Mode">
  <img src="/assets/modeNight.png" width="30%" alt="Night Mode">
  <img src="/assets/modeCave.png" width="30%" alt="Cave Mode">
</p>

### Waypoints
<p align="center">
  <img src="/assets/waypointList.png" width="30%" alt="Waypoint List">
  <img src="/assets/waypointCreate.png" width="30%" alt="Create Waypoint">
  <img src="/assets/waypointEdit.png" width="30%" alt="Edit Waypoint">
</p>

### Map Controls
<p align="center">
  <img src="/assets/mapPanCreateWaypoint.gif" width="40%" alt="Map Panning & Waypoint Creation">
  <img src="/assets/mapRotate.gif" width="40%" alt="Map Rotation">
  <img src="/assets/mapToggles.gif" width="40%" alt="Map Toggles">
  <img src="/assets/mapEntities.gif" width="40%" alt="Map Entities">
</p>




---
## 1.5.4-mp1 (multiplayer patch)
Unofficial fork of Natos Atlas 1.5.4 by **natowb** (https://github.com/natowb/bm-natosatlas), MIT licensed (see LICENSE, unchanged).
Changes: block reads clamped to valid chunk bounds (fixes the Y=-1 crash on join), empty/unreceived chunks are skipped and retried,
per-chunk and per-tick errors are logged instead of crashing, server folder names sanitized (IPv6/special characters), null-safe player/world/biome access.
## 1.5.4-mp2
Unloaded/placeholder chunks are detected with `World.isPosLoaded` + `instanceof EmptyChunk` (pattern from Rei's Minimap Babric port by FarnGitHub, MIT).
Dirty map regions are saved under the old dimension before a dimension change or disconnect; the save queue's pending map is now thread-safe.
## 1.5.4-mp3
Settings > World map > "Block colours": Classic (default, original material colours) or True colour (average of each block's top texture from the active texture pack's terrain.png, cached per block id+meta, grass/leaves biome-tinted, fluids keep classic water colours).
## 1.5.4-mp4
In True colour mode the grass block keeps its Classic colour and biome tint; all other True colour behaviour is unchanged.
## 1.5.4-mp5: import a server's region files (multiplayer)
1. On the server machine, find the world's region folder: `<server folder>/<level-name>/region/` (Overworld, usually `world/region/`) and `<level-name>/DIM-1/region/` (Nether).
2. Join the server once with this mod; it creates `.minecraft/natosatlas/servers/<server folder>/import/` and `import/DIM-1/` (server folder = `host-port`, e.g. `100.73.7.65-25567`).
3. Copy the `r.X.Z.mcr` files into `import/` (Overworld) or `import/DIM-1/` (Nether). Putting them in an `import/region/` subfolder also works. Your files are only read, never changed or deleted.
4. In-game, stand in that dimension, open the map, press Options, then General, then **Import region files**. Progress (chunks done / total) shows on the settings and map screens. It runs in the background in small slices each tick, and you can keep playing. Press the button again to cancel.
Chunks your client currently has loaded are left to live scanning. Corrupt chunks or files are skipped and logged. `.mca` (Anvil) files are ignored.
## 1.5.4-mp6
Options > World map > "Slime seed (blank = server seed)": a per-server manual world seed for the slime chunk overlay (number or text seed). Also fixes the slime chunk formula to match b1.7.3's own check exactly (the original was wrong far out on the X axis).

## Building this fork
Built with JDK 21. From the repo root:
```bash
./gradlew :natosatlas-stationapi:build
```
The jar lands in `natosatlas-stationapi/build/libs/`. The fork is compiled against StationAPI 2.0.0-alpha.5.4 (see `gradle.properties`) and runs fine on alpha.6.4.

## Credit
Natos Atlas is by natowb (https://github.com/natowb/bm-natosatlas), MIT. The unloaded-chunk detection in mp2 follows the pattern from Rei's Minimap Babric port by FarnGitHub (MIT).
