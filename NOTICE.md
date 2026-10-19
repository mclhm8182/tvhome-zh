# NOTICE

`tvhome-zh` is a derivative work of:

**tv-vibehack** — https://github.com/Gathin23/tv-vibehack
Copyright (c) 2026 Gathin — MIT License

**This is an unofficial, community-run derivative.** It is not affiliated with, endorsed by, or
connected to the original author or the upstream project. Please support the upstream project if its
full feature set is what you need.

## What this fork changed

The positioning of this fork: **upstream hands configuration to files and the command line; this fork
hands it to the remote control.** Everything upstream makes you do by editing `config.xml`,
recompiling, or pushing files over ADB has a button on the TV instead.

Added here, not present upstream:

- **Editable dock** — the remote's Menu key (or a "Favourites" pill on the top bar) opens a checklist
  of every installed app, and a long-press on a tile pins or unpins it. Choices are stored on the TV,
  so no file editing or rebuilding is needed.
- **Wallpapers** — a panel with a Bing-daily toggle and a "choose a picture on this TV" option. A new
  picture always replaces the previous one, so the space used never grows.
- **Icons per row, 3 to 6** — cycled from the same panel, with a first-run recommendation based on the
  width the launcher actually gets.
- **Adaptive dock band** — the band behind the dock turns translucent over a wallpaper instead of
  showing up as a black slab, and stays as upstream drew it on a plain black background.
- **Ready-made APK** — upstream ships no release; you build it yourself. This repository publishes
  one.

Still missing here (available upstream): the theme-folder workflow, module cards, the ~250 KB shrunk
build, AOT precompilation, the home-switch shortcut tile, the VPN badge and the Jalali calendar.

Also in this fork:

2. **Eight languages** — the launcher follows the TV's system language. Chinese was completed from
   scratch; German was completed; the Spanish, French, Russian, Catalan and Thai translations are
   upstream's, extended with this fork's new strings. `locales_config.xml` lets Android 13+ give the
   launcher a language of its own.

Everything else — including all default configuration in `config.xml` — is
left as the upstream default, so the launcher ships generic and suits any
brand of TV box. No box-specific or vendor-specific defaults are baked in.

## Fonts

The bundled **Outfit** typeface is licensed separately under the
SIL Open Font License — see `launcher/FONT-LICENSE-OFL.txt`.

## Upstream

Please refer to the original project for its full documentation,
contribution guidelines, and the "Doing it by hand" ADB debloat workflow.