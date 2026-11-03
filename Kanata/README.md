<div align="center">

<img src="docs/icon.svg" width="120" alt="Kanata icon">

# Kanata

**彼方 · "far beyond"**

Watch anime and read manga, manhwa, webtoons and novels, all in one Android app, synced with your AniList account.

![Android 5.0+](https://img.shields.io/badge/Android-5.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7F52FF?logo=kotlin&logoColor=white)
![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue)

</div>

---

## Features

### Watch
- Stream anime from several sources, and switch source or server per episode
- **Picture-in-picture:** press Home and the video keeps playing in a small window, with rewind, play/pause and forward buttons
- Skip intros and outros automatically (timestamps from AniSkip)
- Subtitles, playback speed, quality selection, brightness and volume gestures, double-tap to seek
- Download episodes and follow their progress
- Send a video to an external player or cast app

### Read
- Manga, manhwa, manhua and webtoons from many sources, including the official **WEBTOON** site
- Paged, continuous-paged and continuous (webtoon strip) layouts, any reading direction, dual-page mode
- Manhwa, manhua and webtoons open as a gapless vertical strip automatically
- **Novel reader** for light novels and web novels, with light, sepia, dark and black themes, three fonts, and adjustable size, line spacing and justification; it remembers where you stopped in every chapter

### Track and discover
- Your AniList lists, progress and scores, updated as you watch and read
- Trending, popular and top-rated titles, genres and an airing calendar
- Filter the Read tab to **All, Manga, Manhwa, Manhua or Novels**
- Feed tab with anime news, upcoming releases and AniList community activity (works without logging in)

### Look and feel
- "Liquid glass" interface with light and dark themes
- **Dynamic Island:** a floating pill that shows your last episode and download progress
- Android TV interface

## Sources

| Type | Sources |
|---|---|
| Anime | AniWatch, HiAnime, AniWaves, AnimePahe.ng, plus any mirror you add in **Settings → Sources** |
| Manga, manhwa, webtoons | MangaKakalot, MangaBuddy, MangaPill, MangaDex, MangaReaderTo, AllAnime, Toonily, MangaKatana, Manga4Life, MangaRead, ComickFun, Webtoons, ManhuaPlus |
| Novels | NovelBin, NovelFull |

Sites change and go down. When a source stops working, try another one from the source picker on the title's page.

## Install

**Needs Android 5.0 or newer.**

1. Get the APK: from this repository's **Releases** page once one is published, or [build it yourself](#build).
2. Open it on your phone and allow installing from that app when Android asks.
3. Log in with AniList on the Home tab to sync your lists (optional; browsing works without an account).

Kanata's package name is `app.kanata`, so it installs alongside Saikou or older builds instead of replacing them.

## Build

You need **JDK 17** and the **Android SDK** (API 36). Then, from this folder:

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # app/build/outputs/apk/release/app-release.apk
```

On Windows use `gradlew.bat` instead. Release builds are signed with the debug key, which is fine for installing directly. To publish on the Play Store, set up your own signing key first.

The source tests search each reading and streaming site live and load real chapters and episodes, so they need an internet connection:

```bash
./gradlew testDebugUnitTest
```

## Project layout

All code is under `app/src/main/java/app/kanata/`.

| Folder | What's in it |
|---|---|
| `anilist/` | AniList API: queries, mutations, login |
| `anime/` | Episode list and the video player (including picture-in-picture) |
| `manga/` | Chapter list and the manga reader |
| `novel/` | The novel reader |
| `parsers/` | One parser per site (`anime/`, `manga/`, `novel/`) and the source lists |
| `media/` | Title details, search, characters, studios |
| `feed/`, `island/`, `glass/` | Feed tab, Dynamic Island, glass UI |
| `settings/`, `tv/` | Settings screens and the Android TV interface |

## Adding a source

- **Anime mirror:** no code needed. In **Settings → Sources**, add the site's link; Kanata detects which supported site type it is and tests it before saving.
- **Manhwa site built on WordPress "Madara" theme** (most are): one line in `parsers/manga/Madara.kt`, for example
  ```kotlin
  class ManhuaPlus : Madara("ManhuaPlus", "manhuaplus", "https://manhuaplus.com")
  ```
- **Any other comic or novel site:** subclass `MangaParser` or `NovelParser` in `parsers/` and implement search, chapter list, and pages or text.

Then register it in `MangaSources` or `NovelSources`. **Add new sources at the end of the list**, because each title remembers its chosen source by position. Add a case to `ReadSourcesTest` so the source is checked against the live site.

## Known limitations

- AniList's sign-in page still shows the name "Saikou", because Kanata uses Saikou's AniList app registration for login.
- Webtoons only lists the episodes it gives away free; episodes behind its "Daily Pass" don't appear.
- Reading progress is sent to AniList by chapter number, so chapters a site splits into parts (for example "12-2") don't update it.

## Disclaimer

Kanata doesn't host any content. It finds titles on third-party websites and plays or displays what they serve. Kanata's developers don't control those sites and aren't responsible for what they host.

## Credits

Kanata is maintained by [nitinrawat](https://github.com/NitinRwt).

It is built on [Saikou TV](https://github.com/kemuriXD/SaikouTV) by kemuriXD, which is based on [Saikou](https://github.com/saikou-app/saikou) by the Saikou team. Thanks to both for the foundation. Title data comes from [AniList](https://anilist.co), and intro timestamps come from AniSkip.

## License

Kanata is free software under the [GNU General Public License v3.0](LICENSE), the same license as Saikou TV. You can use, change and share it, but anything you distribute that is built from it must also be released under GPL-3.0, with its source code.
