# SayPiper

Named SayPiper since 12 November 2026: the app label is SayPiper and the application id is `eu.wanderclan.saypiper`. Only the Kotlin namespace, the class names and the database file keep Word Quest, the working title. An offline Android game for children to practise English vocabulary. The child picks their school year (P1–P5 primary, S1–S7 secondary) and the vocabulary, exercise types and difficulty follow from it.

Status: all twelve classes are playable on `main`, with 1,434 words, eight exercise types, practice, rewards, profiles and a parent corner. Not released yet: there is a release build and an emulator smoke test on Android 7 and Android 15, but no release, and nobody has reported a run on a physical device.

- Design: [docs/adr/0001-architecture-and-content-model.md](docs/adr/0001-architecture-and-content-model.md)
- Tracking: Multica issue FRL-3

## Principles

- Fully offline. No network permission, no accounts, no ads, no analytics, no purchases, no backup of a child's data off the device.
- Original content. Mechanics are inspired by lesson-path language apps; nothing is copied from them. Pictures are [OpenMoji](https://openmoji.org/) emoji, licence CC BY-SA 4.0, credited in the app.
- British English (en-GB).

## Building

All you need is Linux x86_64, `curl`, `unzip` and `python3`. No root.

```sh
scripts/setup-toolchain.sh                       # JDK 17 + Android SDK 35 into ~/.cache/android-toolchain
eval "$(scripts/setup-toolchain.sh --print-env)" # JAVA_HOME, ANDROID_HOME, PATH
./gradlew --no-daemon lint testDebugUnitTest assembleDebug
```

The script is idempotent: a second run installs nothing. Set `ANDROID_TOOLCHAIN_DIR` to install elsewhere. On the shared agent host (4 vCPU, 7.9 GB), the install takes under a minute and about 1.1 GB including the downloads; run one Gradle build at a time, with `--no-daemon`. Gradle is capped at 2 GB in `gradle.properties`.

The first build also downloads the Android runtime Robolectric needs for the unit tests (Android 15, listed in `app/build.gradle.kts`) into the Gradle cache. The tests themselves never use the network.

Outputs:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/roborazzi/*.png`: screenshots taken by the Robolectric UI test (CI uploads them as an artifact)

There is no emulator on the agent host: UI is verified with Robolectric tests and CI.

## Installing

SayPiper runs on Android 7.0 or newer and needs no internet connection.

1. On the Android device, open the project's GitHub releases page and download `saypiper-<version>.apk` from the newest release.
2. Open the downloaded file. Android asks once to allow the browser or file manager to install apps ("Install unknown apps"); allow it, then install.
3. To update, install the APK of a newer release in the same way. It installs over the old one and keeps every profile and its progress.

Progress is stored only on the device and is never backed up, so uninstalling the app removes it. The F-Droid build is meant to carry the same signature as the APK published here: the release build is reproducible, and the F-Droid recipe names this APK and its signing certificate (`Binaries`, `AllowedAPKSigningKeys`), so F-Droid ships it only if its own build from the tag matches it byte for byte. Updates then work across both channels. If F-Droid ever signs a version with its own key instead, Android refuses to update one with the other; in that case stay with the channel you installed from.

## Releases

A tag `v<versionName>` on `main` runs `.github/workflows/release.yml`: tests, the R8-shrunk release APK signed with the release key, `scripts/release/check-apk.sh`, and a GitHub release with the APK and its SHA-256. The key and its passwords come from four secrets of the GitHub environment `release`, which only tags matching `v*` may use, and never from the repo: `RELEASE_KEYSTORE_BASE64` (the PKCS12 keystore, base64), `RELEASE_STORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. Without them the release build is unsigned, which is what F-Droid builds from source.

Before tagging a release:

1. Raise `versionCode` and set `versionName` in `app/build.gradle.kts`, and add `fastlane/metadata/android/en-GB/changelogs/<versionCode>.txt`.
2. Run `scripts/release/released-ids.py --version-name <versionName> --version-code <versionCode>` and commit `app/released-ids.json`. `ReleasedIdsTest` then fails when a released unit or word id disappears without being listed under `retired`, is used again after retirement, or moves to another unit (ADR section 4).
3. Run the emulator smoke test (Actions, "Emulator smoke test", build type `release`) on the commit to be tagged.

A tag `test-release-<anything>` runs the same workflow with a key made for that run and publishes nothing; delete the tag afterwards.

A release cannot be taken back from the devices that installed it. Android refuses an APK with a lower version code over a higher one, and an older build may not open the database a newer one has migrated. Deleting a GitHub release only stops further downloads; keep its `v*` tag, which F-Droid builds from. To correct a bad release, publish a new one with a higher `versionCode`, the same signing key and application id, every released id kept or retired (`ReleasedIdsTest`), and a tested forward migration for any schema change (ADR section 9).

The launcher component `eu.wanderclan.wordquest.MainActivity` ships with the first release. From then on it is not renamed without an `activity-alias` of that name: launchers drop the home-screen icon of a component that disappears.

First build installed on a child's device: none yet. Record its version code here. From that build on, every Room schema change ships with a tested migration (ADR section 7).

## Content

Vocabulary lives in `app/src/main/assets/content/` (ADR section 4): `grades.json` (all twelve classes; do not edit), `avatars.json`, and one pack per class in `packs/`. A class whose pack file is absent shows "coming soon".

Pictures are OpenMoji 17.0.0 PNGs chosen by the `emoji` field. After changing content, run

```sh
scripts/sync-pictures
```

It resolves every emoji through OpenMoji's own index, copies the pictures into `content/pictures/`, removes unused ones and rewrites `content/pictures.json`. Commit the result. `ShippedContentTest` fails if any content rule is broken, including a missing or unused picture. Fixture content for the code tests lives in `app/src/test/resources/fixtures/content/`; run `scripts/sync-pictures app/src/test/resources/fixtures/content` after changing it.

P1–P2 (band A) speech is committed as `content/audio/<word id>.ogg`. After adding,
removing or editing a band A word, run the following on Linux x86_64 with Python
and [uv 0.13.7](https://docs.astral.sh/uv/getting-started/installation/) installed (no root):

```sh
scripts/make-audio --report audio-metrics.json
scripts/make-audio app/src/test/resources/fixtures/content
```

Commit the clips; keep the optional metrics report outside `content/`. The script
prints each duration and flags silence, clipping and implausible lengths. These
checks cannot verify pronunciation: listen to every new or changed clip, including
its beginning and ending. No agent can hear. Pronunciation-only spellings belong in
`scripts/audio/overrides.json`, keyed by word id; never change vocabulary to fix speech.

The pipeline is the one approved in FRL-15: Piper 1.8.0, Cori medium at revision
`cf910fb59f1d79ab3b625a63993a108751c6c25d`, CPython 3.10.22, and dependencies pinned
by hashes in `scripts/audio/requirements.lock`. It uses one CPU thread, ONNX seed
42, length 1.10, noise 0.5/0.333, 10-ms RMS trimming at −45 dBFS with 40-ms guards,
−20 dBFS RMS with a −3 dBFS peak ceiling, and FFmpeg 7.0.2 Vorbis quality 3 with
bitexact flags (mono, 22,050 Hz). A full second run is byte-identical on the agent
host; other CPU/platform combinations are not promised. Each word uses a fresh
ONNX session with seed 42, so adding, removing, reordering or changing another
word leaves its clip unchanged. The approved FRL-15 `dog` sample is an exact
regression reference. Clips for words that leave band A are removed.

Fresh installs put CPython, the Python environment, tool wheels, temporary audio
and the checksum-verified voice model outside the repo in `${AUDIO_CACHE_DIR:-~/.cache/wordquest-audio}`. Do not commit the model.
Older environments may still refer to CPython in uv’s previous install location.
After the initial downloads, cached generation works offline. Android builds never
run synthesis and add no speech dependency. The synthetic audio is supplied under
CC BY 4.0 with the conservative training-data attribution in
`app/src/main/assets/licences/piper-cori.txt`.

Run the generator regression tests after installing its environment:

```sh
AUDIO_CACHE_DIR="$HOME/.cache/wordquest-audio" "$HOME/.cache/wordquest-audio/venv/bin/python" -m unittest discover -s scripts/audio
```

The lockfile input is `scripts/audio/requirements.txt`. With uv 0.13.7, regenerate it
using `uv pip compile --python 3.10.22 --generate-hashes scripts/audio/requirements.txt -o scripts/audio/requirements.lock`.

## Code layout

| Package | Holds |
|---|---|
| `content` | pack model, loader, the one validator (rules 1 to 11) |
| `game` | lesson contract, lesson split, generator and capability filter, session, scoring, Leitner. No Android imports. |
| `data` | Room database (schemas in `app/schemas/`), repositories |
| `audio` | bundled clips, the offline device voice and test fakes |
| `ui` | screens, navigation, the `Picture` component |
