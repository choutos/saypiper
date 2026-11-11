# Word Quest

Working title. An offline Android game for children to practise English vocabulary. The child picks their school year (P1–P5 primary, S1–S7 secondary) and the vocabulary, exercise types and difficulty follow from it.

Status: walking skeleton. A child can make a profile, pick a class, play picture lessons from a small P1 seed pack and keep their stars.

- Design: [docs/adr/0001-architecture-and-content-model.md](docs/adr/0001-architecture-and-content-model.md)
- Tracking: Multica issue FRL-3

## Principles

- Fully offline. No network permission, no accounts, no ads, no analytics, no purchases.
- Original content and artwork. Mechanics are inspired by lesson-path language apps; nothing is copied from them.
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

## Code layout

| Package | Holds |
|---|---|
| `content` | pack model, loader, the one validator (rules 1 to 10) |
| `game` | lesson contract, lesson split, generator and capability filter, session, scoring, Leitner. No Android imports. |
| `data` | Room database (schemas in `app/schemas/`), repositories |
| `audio` | the speech interface and a fake |
| `ui` | screens, navigation, the `Picture` component |
