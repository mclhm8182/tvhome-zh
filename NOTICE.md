# NOTICE

`tvhome-zh` is a Chinese-localised derivative work of:

**tv-vibehack** — https://github.com/Gathin23/tv-vibehack
Copyright (c) 2026 Gathin — MIT License

## Modifications made in this repository (Copyright © 2026 mclhm8182)

This repository is **not** an official release of the upstream project. It is
distributed under the same MIT License terms as the original, with the
original copyright notice fully retained.

Modifications:

1. **Simplified Chinese localisation** — added
   `launcher/app/src/main/res/values-zh/strings.xml` (translations for the
   greeting, inputs menu, settings, Wi-Fi status, error messages, and the
   date format), plus a Chinese primary `README.md` with a language switch
   to the upstream English `README.en.md`.

2. **Editable dock** — the dock (the pinned apps along the bottom) is
   editable from the sofa: the remote's Menu key (or a "Favourites" pill on
   the top bar) opens a checklist of every installed app, and a long-press on
   a tile pins or unpins it. Choices are stored on the TV, so no file editing
   or rebuilding is needed. Implemented in `DockStore.java` and
   `MainActivity.dispatchKeyEvent()`. This does not exist upstream, where the
   dock can only be changed by editing `config.xml` and recompiling.

3. **Theme overlay ported from upstream** — `Config.java` reads an optional
   `config.json` (plus `wallpaper.jpg`) from the launcher's external files
   directory, overlaying the built-in `config.xml`, so a theme can be pushed
   to the TV without a rebuild. `tools/set-wallpaper.sh` is a small helper for
   setting a wallpaper.

Everything else — including all default configuration in `config.xml` — is
left as the upstream default, so the launcher ships generic and suits any
brand of TV box. No box-specific or vendor-specific defaults are baked in.

## Fonts

The bundled **Outfit** typeface is licensed separately under the
SIL Open Font License — see `launcher/FONT-LICENSE-OFL.txt`.

## Upstream

Please refer to the original project for its full documentation,
contribution guidelines, and the "Doing it by hand" ADB debloat workflow.