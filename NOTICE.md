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
   date format).
2. **Boot auto-launch** — added `MainActivity.bootLaunchIfDue()`, which
   launches a configured application once per boot, keyed on the system
   `Settings.Global` `boot_count` so it fires exactly once per boot and is
   never re-triggered when the user later presses Home.
3. **Chinese-market defaults** — `config.xml` Dock entries set to a
   common set of Chinese TV apps; Live TV input entry cleared.

## Fonts

The bundled **Outfit** typeface is licensed separately under the
SIL Open Font License — see `launcher/FONT-LICENSE-OFL.txt`.

## Upstream

Please refer to the original project for its full documentation,
contribution guidelines, and the "Doing it by hand" ADB debloat workflow.