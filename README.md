![Logo](Github/Images/ChoraBannerTransparent.png)

# Chora (personal fork)

An Android music player for Subsonic/OpenSubsonic and Navidrome servers, and for music
stored on the device.

**This is a personal fork of [CraftWorksMC/Chora](https://github.com/CraftWorksMC/Chora)**,
built with Claude Code to drive a Fire TV against a Navidrome server. It adds a few
features, fixes bugs that got in the way, and ports fixes from other forks. It is not
affiliated with upstream, publishes no releases, and is not trying to be a distribution.

## Credit where it's due

All of the original work is upstream's. Chora was written by
[CraftWorks](https://github.com/CraftWorksMC) and is maintained at
[CraftWorksMC/Chora](https://github.com/CraftWorksMC/Chora) — please use that repository
rather than this one. Everything here that is not listed under
[Changes in this fork](#changes-in-this-fork) below is upstream's code, unmodified.

The changes listed below were written with **Claude Code**, directed and tested by hand
on the single TV described next. This fork is based on upstream `master` as of 1 October
2026, and is kept current by merging upstream in by hand, so it may lag behind.

Upstream is where bugs should be reported, translations contributed (via
[Crowdin](https://crowdin.com/project/chora)) and donations sent
([PayPal](https://www.paypal.com/donate/?hosted_button_id=REWCVJBKECU34)). The Play Store
and F-Droid builds of Chora are upstream's, not this fork's; do not report a bug you hit
here to them without first confirming it happens in their build too.

## Tested on: one Fire TV, by sideloading

Everything in this fork has been tested by sideloading a release APK onto **a single
Amazon Fire TV Stick 4K Max running Fire OS**, against one Navidrome server. Nothing
else has been exercised:

- **Phone and tablet UI: untested.** Several changes here touch the phone screens as well
  as the TV ones, but only the TV side has been run. Expect rough edges on a phone.
- **Android Auto: untested.**
- **Other servers and providers:** developed against one Navidrome server. A plain
  Subsonic server has never been connected to, and local (MediaStore) playback has never
  been tried.
- **No automated tests.** The repository contains no unit or instrumented tests. A
  successful build proves the code compiles, nothing more.

If you are looking for something well-tested, upstream's releases are the safe choice.

## Changes in this fork

### Features

- **Genres tab**, replacing Songs. A grid of the library's genres, each card a 3:2
  rectangle with a 3×2 collage of album covers and the genre's song and album counts.
  Sortable by name or by size, filterable, with paged song and album lists. Offered only
  by providers that advertise the capability. Phone and TV screens both.
- **Genre grouping.** Genres below a threshold fold into one "Other" card, which sits
  last whatever the sort. The threshold is a setting: *Settings → Appearance → Genre
  Grouping*, off or fewer than 3, 5, 10 or 20 albums (5 by default). Opening the card
  gives the folded genres their own grid.
- **"Appears on" on the artist page.** Albums the artist only guests on — a track on
  someone else's record or a various-artists compilation — now list under the
  discography. Navidrome only, since it is the only provider whose API can answer the
  question. Play and Shuffle all still use the artist's own albums.
- **Artist discography as one grid**, newest first, with the year on each card, instead
  of a row of one album per year.
- **Read the whole artist biography on TV**, with a *More* button that appears only when
  the text is actually cut off. Rendered as markup, scrollable with the remote.
- **Play all or Shuffle all from a genre opens Now Playing**, as the album and playlist
  screens do.
- **TV focus is kept**: backing out of a screen returns focus to the item you left
  rather than the first one, and the shuffle and repeat buttons are tinted while on
  instead of only shifting a shade of background.
- **TV quality of life**: the artist screen loads more artists as you scroll (it used to
  show only the first page), and the album details screen requests a 600px cover instead
  of upscaling the 300px card artwork.
- **Lyrics sources can be reordered, and the length tolerance set.** The order breaks
  ties between results of the same quality, and the tolerance — 0 to 30 s, 5 s by
  default — is what a downloaded candidate's length is judged against. Both live under a
  *Lyrics Providers* entry in Settings; on TV that screen moved out of the Media
  Providers tabs, where it was the second tab nobody would look in, to match the phone.
  On TV the sources carry up/down arrows and the tolerance a pair of buttons, there
  being no drag. Unison ships switched off, upgrades included, with the toggle turning
  it back on.
- **Now Playing credits the source its lyrics came from**, so a source that keeps
  returning the wrong recording can be spotted, and demoted or turned off.
- **The TV player can hide its lyrics pane**, from a button in the transport row that
  remembers the choice, and **open the playing track's album or artist** from the same
  row. The go-to button offers the two screens on top of the player, so Back returns to
  the music; it dims when there is nothing to open, as on a radio station.

### Fixes

- **Unplayable tracks no longer stop playback.** A track the server cannot serve, or one
  the decoder rejects, is skipped; playback gives up only after three failures in a row,
  which means the fault is the output or the server rather than the track.
- **Word-synced lyrics are found more often.** When LRCLIB has no timestamps for the
  album-exact record, the search API is used and the closest matching timed entry is
  taken, filtered by title and by duration so a different edit is not mistaken for the
  recording at hand.
- **Navidrome servers added on TV now work immediately.** The provider was never
  initialised when it replaced the Subsonic one, so playback and cover art failed until
  the app was restarted. This is likely the cause of
  [upstream issue #114](https://github.com/CraftWorksMC/Chora/issues/114).
- **Deleting a playlist on TV asks first.** Long-pressing a playlist deleted it on the
  spot, with no confirmation and no undo, which a remote makes easy to do by accident.
  The dialog deliberately opens on its text rather than a button, so the held OK that
  opened it cannot also confirm the deletion.
- **A refused login is now refused.** The add-server flow built a success response out of
  whatever the server returned and never checked its status. A wrong password was rejected
  only because the error body happened to fail JSON decoding; an error response that
  decodes is one the old code would have accepted and registered as a working server. The
  status the server returned is what decides now, and the dialog reports that the
  credentials were refused instead of printing a stack trace.
- **The TV can add a song to any playlist again.** The add-to-playlist list was a
  fixed-height column that could not scroll, so with more than seven playlists the rest
  were unreachable — and so was the *New Playlist* entry below them, which meant no
  playlist could be created from the TV at all. The list scrolls now.
- **Release builds no longer crash on startup.** R8 stripped the no-arg constructor of a
  migration class, which `MigrationManager` builds reflectively; the release APK died in
  `Application.onCreate` before any UI appeared.
- **NetEase stops supplying words that are not the song's.** It writes a track's credits
  ("作词 : …") as ordinary lyric lines — and for a recording it has no lyrics for, the
  credits are all it returns — so one credit line could win the line-synced tier and sit
  on screen as a header with no song under it. Its search is loose, too: an instrumental
  ("Back to the Future", The Outatime Orchestra) was matched to a same-length Chinese pop
  song that merely shared an album name, and that song's lyrics were shown for it.
  Credits and blank lines are dropped, a candidate agreeing with the track on neither its
  name nor its artist is discarded, and a payload of nothing but credits counts as no
  lyrics.
- **BiniLyrics works again.** The service has moved to lrc.red and the old host answers
  with a redirect, which the client treated as "no lyrics" — silently, for every track.
- **LRCLIB finds titles that carry a version suffix.** Its search matches the track name
  literally, so "Total Eclipse of the Heart (New radio edit mix)" found nothing even
  though the recording was in the database, and the exact-signature lookup 404s for the
  same reason. An empty search is repeated once with the bracketed parts dropped.
- **TV settings that used sliders now have buttons.** A material3 slider cannot be
  steered from a remote at all — its own arrow-key handling takes the presses before the
  screen's does — so both the lyrics tolerance and the scrobble percentage were visible
  and impossible to change. The same copy-and-paste had also left the TV's mobile-data
  bitrate row labelled "Wi-Fi" and opening the Wi-Fi dialog.
- **D-pad focus can reach the lyrics reorder arrows on TV.** They sit inside a row that
  is itself focusable, and directional focus search never moves into the children of the
  focused item, so Right had to be routed onto them by hand, and Left back off them.

### Build

- **Signing from an external keystore.** Release builds read `storeFile` /
  `storePassword` / `keyAlias` / `keyPassword` from the file named by
  `$CHORA_KEYSTORE_PROPERTIES`, or from `keystore.properties` in the project root, and
  fall back to the debug key when neither exists. Keystores are git-ignored.

## Ported from other forks

Several of the fixes here were not written from scratch — they were found by reading the
other forks of Chora and porting what they had already worked out. Thanks to all four:

| Fork | What was taken |
|---|---|
| [Andrezx16/Chora](https://github.com/Andrezx16/Chora) | The leaked lyrics position-tracking loops and player listeners (`561cc5b`), and skipping tracks with channel up/down on the TV remote (`e980624`) |
| [Lauqnan14/Chora](https://github.com/Lauqnan14/Chora) | The same lyrics leak from a different angle (`cb8dfd6`), and shuffle handling (`fbdbbaf`) |
| [davisv7/Chora](https://github.com/davisv7/Chora) | Shuffle handling (`7b69d73`), and signing release builds from an external keystore (`c470144`) |
| [NathanMartinez/Chora](https://github.com/NathanMartinez/Chora) | Confirming playlist deletion on TV ([upstream PR #121](https://github.com/CraftWorksMC/Chora/pull/121)), and rejecting a refused Subsonic login (`b356f2a`) |

As ported, the shuffle fix goes further than either original: `SongHelper.play` /
`SongHelper.shuffle` now set shuffle mode explicitly at every call site, which also fixes
the phone album Shuffle never enabling shuffle mode, the TV artist Shuffle picking its
random index from the wrong list, and a crash on shuffling an empty list.

## Known gaps

Three fixes worth having are not here: the transcoded-stream duration bug, support for
LRC lines carrying several timestamps, and a 10-band equaliser —
[LittleYe233/Chora](https://github.com/LittleYe233/Chora) has all three. The middle one
is a live bug upstream as well: a line with more than one timestamp uses the first and
leaves the rest in the text.

## Building

JDK 21 and Android SDK platform 37 are required. The platform is `platforms;android-37.0`
— `platforms;android-37` does not exist. There is no `local.properties` in the
repository; either create one with `sdk.dir=…` or point `ANDROID_HOME` at the SDK.

```bash
ANDROID_HOME=/path/to/sdk ./gradlew assembleRelease   # app/build/outputs/apk/release/
ANDROID_HOME=/path/to/sdk ./gradlew assembleDebug     # app/build/outputs/apk/debug/
```

A clean release build takes about two minutes and emits a number of harmless deprecation
and native-library-stripping warnings.

Release builds are signed from `keystore.properties` in the project root, or from the
file named by `$CHORA_KEYSTORE_PROPERTIES`;
[keystore.properties.example](keystore.properties.example) shows the format. With
neither file present the debug key is used, so fresh clones and CI still build.

CI (`.github/workflows/build.yml`) builds a debug APK on every push and uploads it as an
artifact — the easiest way to get an installable APK without a local toolchain.

### Sideloading to a TV

```bash
adb connect <tv-ip>:5555
adb install -r app/build/outputs/apk/release/app-release.apk
```

The APK is universal (arm64-v8a, armeabi-v7a, x86, x86_64), so it covers 32-bit Fire TV
devices as well. Fire OS 6 and newer report themselves as televisions and get the TV
interface automatically; Vega OS Fire TV devices cannot run APKs at all.

Two things to know before installing:

- **The signing key must match the installed build.** Android refuses to update an app
  signed with a different key (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). This fork keeps
  upstream's `com.craftworks.music` application id, so it will not install over a Play
  Store or F-Droid build of Chora until that build is uninstalled.
- **Uninstalling loses your servers.** Provider configuration, including credentials, is
  encrypted with a key held in the device's Keystore and is not backed up, so a reinstall
  means re-entering the server. Keep whatever key you sign releases with.

Design, architecture and further build and deployment notes are in [CLAUDE.md](CLAUDE.md).

## License

Apache License 2.0, as upstream — see [LICENSE](LICENSE). Copyright belongs to the
original authors; the changes listed above are offered under the same terms.

> Lyrics icon provided by [Remix Icon](https://remixicon.com/ "Remix Icon")
> Other icons are provided by [Google Icons](https://fonts.google.com/icons "Google Icons")
