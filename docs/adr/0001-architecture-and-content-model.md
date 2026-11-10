# ADR 0001 — Architecture and content model

- Status: **Proposed**. Second opinion requested from Architect Astra; owner sign-off pending.
- Date: 2026-11-10
- Tracking: FRL-3

## 1. Verdict

Build a native Android app in Kotlin and Jetpack Compose that works fully offline, with no backend. Vocabulary ships inside the app as one JSON file per school year. Pictures are emoji, speech is the device's own text-to-speech, progress is stored on the device. Lessons are generated from word lists by a pure-Kotlin game core, so adding vocabulary never needs a code change.

This is the smallest design that delivers the request, and each part has an upgrade path that does not require a rewrite (section 9).

## 2. Context

The request: an Android game to practise English vocabulary, in the lesson-path style of language apps, for children. The child selects their class; level and vocabulary adapt. Classes are P1–P5 (primary) and S1–S7 (secondary). Code lives in a private repo under `choutos`.

### Facts (measured 2026-11-10)

| What | Result |
|---|---|
| Prior art in the same account | `choutos/europe-capitals-quiz`: Kotlin 2.1.20, AGP 8.9.2, Compose BOM 2025.04.01, Material 3, minSdk 24, compile/target SDK 35, JDK 17, no network permission. CI on `ubuntu-latest` builds and publishes a debug APK; an emulator screenshot job runs in GitHub Actions. All recent runs green. |
| Agent build host | One VPS shared by every agent in the workspace: x86_64, 4 vCPU, 7.9 GB RAM, 77 GB free disk. No JDK, no Android SDK, no root, no `/dev/kvm` (so no emulator). |
| Can agents build Android there? | Yes. JDK 17 (Temurin) plus Android cmdline-tools, `platforms;android-35`, `build-tools;35.0.0` and platform-tools installed without root in about 70 s (780 MB). A cold `./gradlew testDebugUnitTest assembleDebug` of the prior app took 294 s, exit 0, and left a 1.1 GB Gradle cache. |
| School-year structure | P1–P5 plus S1–S7 is the European Schools structure (5 + 7 years, ages 6 to 18). Published second-language (L2) targets there: A2 by end of primary, B1 by S3, B2 by S5, C1 by S7 ([Nuffic summary](https://www.nuffic.nl/en/node/124)). |

### Inferences (not confirmed by the owner)

- The children are learning English as a second language, not native speakers.
- The app is for a small known group of children first, not a public launch.
- British English is the expected variety.

### Unknown

- The children's first language, and whether translation hints are wanted.
- Whether the app goes to Google Play or is installed from an APK.

## 3. Decisions

| # | Decision | Main reason |
|---|---|---|
| D1 | Native Android: Kotlin, Jetpack Compose, Material 3, single `app` module. Start from the versions proven in `europe-capitals-quiz`; upgrade only with a stated reason. | Same stack as a shipped app in this account, proven to build on the agent host. |
| D2 | Offline only. No backend, no accounts, no `INTERNET` permission, no analytics, ads or purchases. | Children's data never leaves the device, and there is nothing to operate. |
| D3 | Content is versioned JSON in `app/src/main/assets/content/`, one pack per school year, parsed with kotlinx.serialization. | Vocabulary changes are data-only and reviewable as diffs. |
| D4 | Pictures are emoji, rendered through EmojiCompat with a bundled font. No bitmap or vector artwork in v1. | Zero artwork to license or draw; identical on every device. |
| D5 | Speech uses Android `TextToSpeech`, locale en-GB, slower rate for primary. No bundled audio in v1. | No audio assets to produce or ship. |
| D6 | Lessons are generated, not authored: a pure-Kotlin game core builds exercises from words by school-year band. | A word list is enough to produce a playable unit. |
| D7 | Progress is stored in Room (SQLite). | "Which words are due for this child today" is a relational query. |
| D8 | Several local profiles per device (nickname, avatar emoji, class). | Siblings share tablets; retrofitting profiles later means migrating every table. |
| D9 | No lives or hearts and no timers. Wrong answers come back at the end of the lesson. | Lock-outs punish the children who need the practice most. |
| D10 | Verification is JVM-first: unit tests for the game core and content, Robolectric tests and screenshots for UI, CI as the gate. | The agent host has no emulator. |

## 4. Content model

Two kinds of file under `app/src/main/assets/content/`.

`grades.json` is the only place that maps a class to a level:

```json
{
  "schemaVersion": 1,
  "grades": [
    { "id": "p1", "label": "P1", "cycle": "primary", "cefr": "pre-A1", "band": "A", "pack": "packs/p1.json" },
    { "id": "s5", "label": "S5", "cycle": "secondary", "cefr": "B2", "band": "D", "pack": "packs/s5.json" }
  ]
}
```

`packs/<grade>.json` holds themed units of words:

```json
{
  "schemaVersion": 1,
  "grade": "p1",
  "units": [
    {
      "id": "p1-animals",
      "title": "Animals",
      "emoji": "🐶",
      "words": [
        { "id": "dog", "text": "dog", "pos": "noun", "emoji": "🐶", "example": "The {dog} is big." },
        { "id": "bat-animal", "text": "bat", "pos": "noun", "emoji": "🦇" }
      ]
    }
  ]
}
```

A word with no picture carries a definition instead:

```json
{ "id": "reliable", "text": "reliable", "pos": "adjective",
  "definition": "that you can trust to do what is needed",
  "example": "She is a {reliable} friend who always keeps her promises." }
```

### Rules, enforced by one unit test that loads every pack through the real loader

1. `id` is lowercase kebab-case and unique across all packs. It is the key for a child's progress, so it is never renamed or reused. A word that is withdrawn is deleted; its id stays retired.
2. Band A (P1–P2): every word has an `emoji`. Other bands: a word has an `emoji`, or both a `definition` and an `example`.
3. `example` contains the word once, wrapped in braces, spelled exactly as `text`. The braces mark the gap for gap-fill and are stripped for display.
4. `definition` does not contain the word itself.
5. Within a pack, no two words share an `emoji` and no two words share a `text`. Otherwise picture questions have two right answers.
6. A unit has 6 to 12 words. `text` is at most 24 characters.
7. `pos` is one of `noun`, `verb`, `adjective`, `adverb`, `phrase`, `other`.

There is deliberately one validator: the app's own loader, run in CI. A separate schema file would be a second contract that can drift from the code.

### Provenance

Word lists are written by the team, by theme and level. Published lists (Cambridge exam lists, Oxford 3000/5000, English Vocabulary Profile) are not copied. An openly licensed list may be used to cross-check levels only after its licence has been read and recorded in the PR.

## 5. School year to level

Data, not code: changing this table is an edit to `grades.json` and the packs.

| Class | Age | Target level | Band | Words in v1 |
|---|---|---|---|---|
| P1 | 6–7 | pre-A1 | A | 60 |
| P2 | 7–8 | pre-A1 to A1 | A | 80 |
| P3 | 8–9 | A1 | B | 100 |
| P4 | 9–10 | A1 to A2 | B | 100 |
| P5 | 10–11 | A2 | B | 120 |
| S1 | 11–12 | A2+ | C | 120 |
| S2 | 12–13 | A2+ to B1 | C | 120 |
| S3 | 13–14 | B1 | C | 140 |
| S4 | 14–15 | B1+ | D | 140 |
| S5 | 15–16 | B2 | D | 150 |
| S6 | 16–17 | B2+ | D | 150 |
| S7 | 17–18 | C1 | D | 150 |

About 1,430 words in total. The anchors (A2 at P5, B1 at S3, B2 at S5, C1 at S7) are the European Schools L2 targets; the years in between are interpolated by me. If the children follow a gentler curriculum, the fix is to shift the table, and a child can always pick a lower or higher class in the app.

## 6. Game model

Structure: class → units (themes) → words. A unit of 8 words yields two introduction lessons of 4 words each and one mixed lesson. A lesson is about 10 exercises and takes two to three minutes.

Exercise types:

| Type | The child | Needs |
|---|---|---|
| Picture to word | sees an emoji, picks the word | emoji |
| Word to picture | reads or hears a word, picks the emoji | emoji |
| Listen and pick | hears a word, picks the emoji or the written word | speech |
| Match pairs | pairs four or five words with their emoji or definitions | emoji or definition |
| Spell with tiles | arranges letter tiles | emoji or speech; single word of at most 10 letters |
| Definition to word | reads a definition, picks the word | definition |
| Gap-fill | picks the word that completes the sentence | example |
| Type the word | types the word from a definition or from speech | definition or speech |

Bands decide the mix:

| Band | Classes | Mix |
|---|---|---|
| A | P1–P2 | Picture and listening types only, three choices, the word is spoken automatically. A child who cannot read yet can still succeed. |
| B | P3–P5 | Adds spelling tiles, and gap-fill from P4. Four choices. |
| C | S1–S3 | Adds definitions and typing. Fewer picture questions. |
| D | S4–S7 | Definitions, gap-fill, typing, and pairs of word and definition. |

Rules:

- **Capability filter.** The generator only emits an exercise the word and the device can support (`supports(type, word, environment)`). A word with no supported type is skipped; a unit with fewer than four playable words shows as "coming soon". This is what keeps the app usable when a device has no English voice, and what lets content and exercise types land in any order.
- **Distractors** come from the same pack, same part of speech where possible, and never share an emoji or text with the answer.
- **Mistakes** are re-queued at the end of the lesson, at most twice per word. Stars: three for no mistakes, two for one or two, one for finishing.
- **Spaced repetition** is a five-box Leitner scheme per profile and word: a correct answer moves the word up one box, a wrong answer sends it back to box 1. Boxes are due after 0, 1, 3, 7 and 21 days. "Practice" serves up to ten due words. Chosen over SM-2 because it has no tuning parameters and a child or parent can understand it.
- **Rewards**: stars per lesson, experience points, a daily streak based on the device's local date, and a crown for a finished unit. No leaderboards, since those need a network.
- **Units unlock in order** within a class. Changing class is always allowed and keeps all progress.
- The generator takes a random seed, so every lesson can be reproduced in a test.

## 7. App structure

Single activity, Compose Navigation, ViewModels with one-way state, manual dependency wiring (no DI framework).

| Package | Holds | Android imports |
|---|---|---|
| `content` | pack model, loader, validation | assets only |
| `game` | exercise generator, lesson session state machine, scoring, Leitner scheduling | none |
| `data` | Room database, repositories | yes |
| `audio` | speech behind a small interface, with a silent fake for tests | yes |
| `ui` | screens: profile, class picker, path, lesson, result, practice, parent corner | yes |

`game` having no Android imports is the rule that keeps tests fast and keeps the core portable.

Room tables: `profile`, `word_progress` (profile, word id, box, due day, correct and wrong counts), `lesson_result` (profile, unit, lesson, stars), `daily_activity` (profile, day, points). The schema may change freely until the first build is installed on a child's device. From then on every schema change ships with a tested migration.

## 8. Options considered

**Stack**

| Option | For | Against |
|---|---|---|
| Kotlin + Compose (chosen) | Proven here end to end; smallest APK; direct access to speech and emoji APIs. | Android only. An iOS version would need a new UI. |
| Flutter | iOS and web from one codebase; good headless widget tests. | No precedent in this account, unproven on the agent host, larger APK. The right choice if iOS becomes a near-term need. |
| Web app in a wrapper | Fastest to iterate, previewable in a browser. | Speech, audio and offline behaviour are less reliable in a WebView; weaker game feel. |
| Game engine (Godot, Unity) | Strong for animation-heavy games. | Wrong tool for a text and button quiz: text layout, accessibility and speech all get harder. |

**Pictures**: emoji with a bundled font (chosen) against a bundled subset of OpenMoji SVGs (consistent art, CC BY-SA attribution, a conversion pipeline to maintain) against commissioned art (best result, real cost and delay).

**Speech**: on-device text-to-speech (chosen) against pre-rendered audio per word (consistent quality, about 10 to 20 MB of assets and a generation step with its own licence terms).

**Backend**: none (chosen) against a hosted backend for accounts and sync. A backend brings children's personal data, consent handling and running costs. Revisit only if progress must follow a child across devices or a teacher needs a dashboard.

## 9. Tradeoffs and exit paths

| Decision | What we give up | Exit path |
|---|---|---|
| Android native | iOS | Content JSON is stack-neutral, and `game` is pure Kotlin, portable to Kotlin Multiplatform. |
| No backend | Sync, remote content updates, usage data | Packs are versioned files, so they can later be downloaded; repositories sit behind interfaces. |
| Emoji | Art direction; words with no emoji have no picture | Add an optional `image` field per word. Additive, no migration. |
| Device speech | Consistent voice quality | Add an optional `audio` asset per word. Additive. |
| Leitner | Fine-grained scheduling | Scheduling is one class in `game`; stored data (box, due day, counts) is enough to seed another algorithm. |

Rollback for the project as a whole: nothing here touches an existing system. Any PR can be reverted on its own.

## 10. Risks and how each would show up

| Risk | How it shows up | Mitigation |
|---|---|---|
| Vocabulary is too easy or too hard for a class | Children get everything right, or give up. With no analytics we only learn this from the children and parents. | The level table is data; a child can change class; review agents check each pack against its target level. |
| No English voice on a device, or a poor one | Listening exercises are silent or sound wrong. | Detect at start; the capability filter drops listening types and hides speaker buttons. Android 11+ needs a `<queries>` entry for the speech service or the engine is invisible to the app. |
| Emoji missing on older Android versions | Empty boxes instead of pictures. | Bundled emoji font through EmojiCompat; a test checks every emoji used against the bundled font. The APK size cost is measured in the first PR. |
| Emoji is ambiguous as a picture | A child cannot tell what the picture means (🥶 for "cold"). | Authors use emoji only where the meaning is obvious, otherwise a definition. Review checks this per pack. |
| Progress lost on update | A child's stars vanish after an app update. | Exported Room schemas and migration tests from the first installed build. |
| Shared build host runs out of memory | Gradle builds are killed mid-run when several agents build at once. | `--no-daemon`, `-Xmx2g`, at most two build-heavy issues in parallel, reviewers rely on CI results. |
| CI minutes on a private repo | Workflows stop running when the monthly quota is used up. | Build on pull requests and on `main` only; emulator job is manual. |
| Play Store rules for children's apps | Listing rejected. | Not needed for APK installs. If Play is chosen: privacy policy, target-audience declaration, signing key. Tracked as its own issue. |
| Looks like a copy of an existing product | Takedown or rejection. | Own name, mascot, colours and content; mechanics only. |

## 11. Open questions for the owner

Defaults are in place, so none of these blocks the build.

1. Level targets: European Schools L2 anchors (default), or a gentler curve?
2. Distribution: APK from CI (default for now), or Google Play?
3. English variety: British (default) or American?
4. First language of the children, and are translation hints wanted? Default: none; exercises use pictures, speech and English definitions.
5. Name: "Word Quest" is a working title and has not been checked against existing apps.
