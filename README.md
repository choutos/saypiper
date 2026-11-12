# Word Quest

Working title: the name is already used by other apps and will change before the first release. An offline Android game for children to practise English vocabulary. The child picks their school year (P1–P5 primary, S1–S7 secondary) and the vocabulary, exercise types and difficulty follow from it.

Status: walking skeleton. A child can make a profile, pick a class, play picture lessons from a small P1 seed pack and keep their stars.

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

The first build also downloads the two Android runtimes Robolectric needs for the unit tests (about 350 MB, listed in `app/build.gradle.kts`) into the Gradle cache. The tests themselves never use the network.

Outputs:

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/roborazzi/*.png`: screenshots taken by the Robolectric UI test (CI uploads them as an artifact)

There is no emulator on the agent host: UI is verified with Robolectric tests and CI.

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
