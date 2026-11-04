# Third-party notices

Sonorus itself remains GPL-3.0-or-later and retains the copyright/SPDX notices from the upstream Rhythm source. Dependencies and bundled assets remain under their own terms.

| Component or asset | License / status | Project or source |
| --- | --- | --- |
| alphaTab 1.8.4 with Sonorus Android audio worker patch | MPL-2.0 | <https://github.com/CoderLine/alphaTab>; patched source and rebuild script in `third_party/alphatab-1.8.4/` |
| AndroidX / Jetpack Compose / Media3 / Room / WorkManager / Glance | Apache-2.0 | <https://github.com/androidx/androidx> |
| OkHttp / Retrofit / Gson / Kotlin coroutines | Apache-2.0 | respective upstream projects listed in the app's Open Source Libraries sheet |
| JAudioTagger | LGPL family; verify the exact bundled artifact notice before public release | <https://github.com/ijabz/jaudiotagger> |
| Geom font | bundled upstream; verify the exact font file's OFL notice before public release | `app/src/main/res/font/geom.ttf` |
| Material Symbols font | bundled upstream; verify the exact font file's license notice before public release | `app/src/main/res/font/material_symbols_outlined.ttf` |
| `sonivox.sf2` SoundFont | inherited binary; embedded metadata identifies `SONiVOX EAS GM Wavetable`, Sonic Network, Inc. The project owner explicitly approved distribution; no separate upstream license file is bundled. | `app/src/main/assets/sonivox.sf2` |

The MusicXML examples, Catalog music, scores, lyrics, covers, screenshots, and Sonorus brand artwork are separate content/assets. Their inclusion does not make them GPL-licensed. The maintainer must retain evidence of redistribution rights for every release asset.
