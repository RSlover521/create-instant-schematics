# Create: Instant Schematics

![Create: Instant Schematics icon](https://cdn.jsdelivr.net/gh/RSlover521/create-instant-schematics@main/src/main/resources/icon.png)

<p align="center">
  <a href="https://github.com/RSlover521/create-instant-schematics/releases"><img alt="GitHub release" src="https://img.shields.io/github/v/release/RSlover521/create-instant-schematics?style=for-the-badge"></a>
  <a href="https://github.com/RSlover521/create-instant-schematics/blob/main/LICENSE"><img alt="License" src="https://img.shields.io/github/license/RSlover521/create-instant-schematics?style=for-the-badge"></a>
  <a href="https://github.com/RSlover521/create-instant-schematics/issues"><img alt="Issues" src="https://img.shields.io/github/issues/RSlover521/create-instant-schematics?style=for-the-badge"></a>
  <img alt="Minecraft 1.20.1" src="https://img.shields.io/badge/Minecraft-1.20.1-62b47a?style=for-the-badge">
  <img alt="Forge 47.4.x" src="https://img.shields.io/badge/Forge-47.4.x-f16436?style=for-the-badge">
  <img alt="Create 0.5.1.f" src="https://img.shields.io/badge/Create-0.5.1.f-4da3d9?style=for-the-badge">
</p>

## Overview

Have you ever wondered why the Create's Schematic Table feels like it takes forever to load large schematics?

Why can't we just skip the waiting?

Well... now you can.

**Create: Instant Schematics** is a lightweight Create addon that lets server players instantly convert a held **Empty Schematic** into a written **Schematic** for an existing server-side `.nbt` file.

Instead of waiting for the Schematic Table to upload large files, simply run a command and start building immediately.

The mod does **not** introduce a custom schematic format, GUI, uploader, printer, or hologram renderer. Instead, it reuses Create's existing schematic system, allowing Create to handle hologram previews, placement settings, and printing exactly as intended.

---

## Features

* Instantly load an existing `.nbt` schematic with a command.
* Recycle a written schematic back into an Empty Schematic.
* Uses Create's native schematic preview and placement behavior.
* Supports filenames with spaces when enclosed in quotes.
* Validates schematic paths to prevent directory traversal.
* Preserves schematic files when unloading a schematic item.
* Synchronizes the client cache so Create can immediately display the hologram preview.

---

## How To Use This Mod

1. Install **Create: Instant Schematics**. See the installation instructions below.

2. Choose the workflow that matches your setup:

    * **Singleplayer:** Simply use the provided commands to load or unload schematics.
    * **Multiplayer Server:** Continue with the steps below.

3. Ensure the desired `.nbt` schematic is available on the server under:

   ```text
   schematics/uploaded/<playerName>/
   ```

   Once the file is there, use `/cischematic load <filename>` to instantly convert a held Empty Schematic into a written Create Schematic.

4. If your server host does **not** allow direct file uploads (for example, Aternos), upload the schematic once using Create's normal Schematic Table. After the file has been uploaded to the server, you can reuse it instantly with the provided commands without waiting for another upload.

---

## Commands

```mcfunction
/cischematic load <filename>
/cischematic unload
```

Short aliases are also available:

```mcfunction
/cis load <filename>
/cis unload
```

If the filename does not end with `.nbt`, the extension is appended automatically.

Example:

```mcfunction
/cischematic load "example.nbt"
```

---

## Schematic Files

Schematics are loaded from:

```text
schematics/uploaded/<playerName>/<filename>.nbt
```

For example, player `Steve` would load:

```text
schematics/uploaded/Steve/example.nbt
```

Running `/cischematic unload` only recycles the held schematic item. It does **not** modify or delete the `.nbt` file.

---

## Installation

1. Install Minecraft **1.20.1**.
2. Install Forge **47.4.x**.
3. Install Create **0.5.1.f**.
4. Place **Create: Instant Schematics** into your `mods` folder.
5. Launch the game or server.

Java 17 is required.

---

## Requirements

| Dependency | Version |
| ---------- | ------- |
| Minecraft  | 1.20.1  |
| Forge      | 47.4.x  |
| Java       | 17      |
| Create     | 0.5.1.f |

---

## Notes

Very large schematics may still cause short freezes while Create loads and renders the hologram preview. This mod bypasses the upload process but intentionally relies on Create's native rendering and placement pipeline, so overall performance for extremely large schematics remains limited by Create and Minecraft.

## Links

- GitHub: [RSlover521/create-instant-schematics](https://github.com/RSlover521/create-instant-schematics)
- Issues: [Report a bug](https://github.com/RSlover521/create-instant-schematics/issues)
- License: [MIT](LICENSE)
