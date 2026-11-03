# Kanata

**Kanata** (彼方, "far beyond") is an anime, manga, webtoon and novel app for Android, maintained by [nitinrawat](https://github.com/NitinRwt).

- Browse and track everything with your AniList account
- Stream anime from AniWatch, HiAnime, AniWaves and AnimePahe.ng, with picture-in-picture that keeps playing when you leave the app
- Read manga, manhwa and manhua, with a continuous vertical mode that turns on automatically for webtoons (sources include the official WEBTOON site)
- Read light novels and web novels from NovelBin and NovelFull in a text reader with adjustable theme, font, size and spacing
- Liquid glass interface with light and dark themes

## Building

```
gradle assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

The source tests check each site live and need an internet connection:

```
gradle testDebugUnitTest
```

## Credits

Kanata is based on [Saikou TV](https://github.com/kemuriXD/SaikouTV) by kemuriXD, which is based on [Saikou](https://github.com/saikou-app/saikou) by the Saikou team. Thanks to them for the foundation this app is built on.

## License

Kanata is free software under the [GNU General Public License v3.0](LICENSE), the same license as Saikou TV. You can use, change and share it, but anything you distribute that is built from it must also be released under the GPL-3.0 with its source code.
