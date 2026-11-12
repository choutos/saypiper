# ADR 0001 — Architecture and content model

- Status: **Accepted**, revision 2. Reviewed by Architect Astra in FRL-4; merging this revision is the owner's sign-off. Section 12 lists what the review changed; section 11 holds the owner's decisions of 2026-11-10.
- Date: 2026-11-10
- Tracking: FRL-3

## 1. Verdict

Build a native Android app in Kotlin and Jetpack Compose that works fully offline, with no backend. Vocabulary ships inside the app as one JSON file per school year. Pictures are bundled images chosen by emoji, the youngest classes hear their words from bundled clips and the others from the device's own offline English voice, progress is stored on the device and never backed up off it. Lessons are generated from word lists by a pure-Kotlin game core, so adding vocabulary never needs a code change.

This is the smallest design that delivers the request, and each part has an upgrade path that does not require a rewrite (section 9).

## 2. Context

The request: an Android game to practise English vocabulary, in the lesson-path style of language apps, for children. The child selects their class; level and vocabulary adapt. Classes are P1–P5 (primary) and S1–S7 (secondary). Code lives in a private repo under `choutos`.

### Facts (measured 2026-11-10)

| What | Result |
|---|---|
| Prior art in the same account | `choutos/europe-capitals-quiz`: Kotlin 2.1.20, AGP 8.9.2, Compose BOM 2025.04.01, Material 3, minSdk 24, compile/target SDK 35, JDK 17, no network permission. CI on `ubuntu-latest` builds and publishes a debug APK; an emulator screenshot job runs in GitHub Actions. All recent runs green. Its manifest sets `android:allowBackup="true"`. |
| Agent build host | One VPS shared by every agent in the workspace: x86_64, 4 vCPU, 7.9 GB RAM, 77 GB free disk. No JDK, no Android SDK, no root, no `/dev/kvm` (so no emulator). |
| Can agents build Android there? | Yes. JDK 17 (Temurin) plus Android cmdline-tools, `platforms;android-35`, `build-tools;35.0.0` and platform-tools installed without root in about 70 s (780 MB). A cold `./gradlew testDebugUnitTest assembleDebug` of the prior app took 294 s, exit 0, and left a 1.1 GB Gradle cache. |
| School-year structure | P1–P5 plus S1–S7 is the European Schools structure (5 + 7 years, ages 6 to 18). Published second-language (L2) targets there: A2 by end of primary, B1 by S3, B2 by S5, C1 by S7 ([Nuffic summary](https://www.nuffic.nl/en/node/124); checked again in FRL-4 against the [European School Luxembourg I table](https://www.euroschool.lu/secondary-s4-s5-subject-choice-booklet/)). |
| Cost of a bundled emoji font | The font inside `androidx.emoji2:emoji2-bundled` 1.5.0 and 1.6.0 is 10,773,992 bytes, 9,864,941 compressed (measured in FRL-4, re-measured for this revision). Android's own guide calls it "over 10 MB" and recommends downloadable fonts instead. |
| What the default emoji setup does | Compose text depends on `emoji2` (`ui-text` 1.8.0 in the BOM we start from pulls in `emoji2` 1.4.0), whose default initialiser asks a font provider for an emoji font at app start; on devices with Google Play services that request goes to, and is logged by, Google Play services ([Android guide](https://developer.android.com/develop/ui/views/text-and-emoji/emoji2)). |
| Cost of bundled pictures | 40 everyday pictures from [OpenMoji 17.0.0](https://github.com/hfg-gmuend/openmoji/releases/tag/17.0.0) as 618 px PNG total 423,848 bytes, about 10.6 KB each (measured in FRL-4). Licence: CC BY-SA 4.0, attribution required. OpenMoji's index strips the variation selector from single-character file names (`✏️` is `270F.png`) but keeps it inside joined sequences. |
| Device speech | A text-to-speech voice can require the network (`Voice.isNetworkConnectionRequired()`) or be listed without being installed (`KEY_FEATURE_NOT_INSTALLED`). A successful `setLanguage` does not prove that speech works offline. |
| Bundled audio | Twenty sample words in Piper's "Cori" voice came to 115,543 bytes as mono OGG Vorbis, 5.0 to 6.6 KB a clip: about 0.8 MB for 140 words. Two full generation runs on the agent host gave identical files. Licence chain as documented in FRL-15 and not audited further: Piper GPL-3.0-or-later, the voice MIT, its training data (Hi-Fi TTS, from Cori Samuel's LibriVox readings) CC BY 4.0. |
| Android backup | Auto Backup uploads an app's databases to the user's Google Drive unless the app opts out; on Android 12 and later `allowBackup="false"` alone does not stop device-to-device transfer on every manufacturer ([Android guide](https://developer.android.com/identity/data/autobackup)). |
| Google Play | Since 31 August 2026 new apps and updates must target API 36; the extension ended on 1 November 2026 ([Play Console help](https://support.google.com/googleplay/android-developer/answer/11926878)). |
| F-Droid | The public F-Droid catalogue builds every app itself, from publicly accessible source under a free licence and with a free toolchain. It forbids Google Play services and tracking libraries. An app "should have been actively developed for at least 3 months" unless it has proven users, its author already maintains apps on F-Droid, or it is built for an event; the author commits to ongoing maintenance ([inclusion policy](https://f-droid.org/docs/Inclusion_Policy/)). Assets under a Creative Commons licence with a non-commercial or no-derivatives clause count as non-free; BY-SA has neither ([anti-features](https://f-droid.org/docs/Anti-Features/)). It publishes a developer's own signature only when the build is reproducible ([reproducible builds](https://f-droid.org/docs/Reproducible_Builds/)). A self-hosted F-Droid repository can serve any APK and signs its own index ([guide](https://f-droid.org/docs/Setup_an_F-Droid_App_Repo/)). Read on 2026-11-10. |

### Confirmed by the owner (2026-11-10)

- British English.
- Level targets follow the European Schools L2 anchors.
- No first language is assumed: the app is in English only.
- Distribution is an APK from CI and, if it accepts the app, F-Droid's public catalogue. Not Google Play.
- Pictures are OpenMoji for the first version.

### Inferences (not confirmed by the owner)

- The children are learning English as a second language, not native speakers.
- With F-Droid as a channel the app can reach families we do not know, on devices we have not seen, including devices without Google apps.

### Unknown

- Which devices the children use, and whether those have an offline English voice.
- The app's name. "Word Quest" is already used by others.

## 3. Decisions

| # | Decision | Main reason |
|---|---|---|
| D1 | Native Android: Kotlin, Jetpack Compose, Material 3, single `app` module. Start from the versions proven in `europe-capitals-quiz`; upgrade only with a stated reason. | Same stack as a shipped app in this account, proven to build on the agent host. |
| D2 | Offline only. No backend, no accounts, no `INTERNET` permission, no analytics, ads or purchases. No cloud backup and no device-to-device transfer of app data. No request to Google Play services for fonts. | Children's data never leaves the device, and there is nothing to operate. |
| D3 | Content is versioned JSON in `app/src/main/assets/content/`, one pack per school year, parsed with kotlinx.serialization. | Vocabulary changes are data-only and reviewable as diffs. |
| D4 | Pictures are bundled images: one OpenMoji 17.0.0 colour PNG per emoji used in the content, selected by the `emoji` field of a word. The app never draws an emoji as text. | Identical on every device and in every test screenshot; 0.4 MB for 40 pictures where the font costs 9.4 MB before the first word; a reviewer can open the exact picture a child will see. |
| D5 | Speech has two sources. Every P1–P2 word ships with a clip, generated once with a free synthesiser (Piper, British voice "Cori") and committed as an asset. Every other word is spoken by Android `TextToSpeech` with an installed, offline English voice, British preferred, slower for primary. | The youngest children cannot read a fallback, so their words must sound on any device, including one with no speech engine. The owner chose the voice by ear. Older children can read when a device is silent. |
| D6 | Lessons are generated, not authored: a pure-Kotlin game core builds exercises from words by school-year band. | A word list is enough to produce a playable unit. |
| D7 | Progress is stored in Room (SQLite). Finishing a lesson is one transaction. | "Which words are due for this child today" is a relational query; a half-saved lesson must not exist. |
| D8 | Several local profiles per device (nickname, avatar, class). | Siblings share tablets; retrofitting profiles later means migrating every table. |
| D9 | No lives or hearts and no timers. Wrong answers come back at the end of the lesson. | Lock-outs punish the children who need the practice most. |
| D10 | Verification is JVM-first: unit tests for the game core and content, Robolectric tests and screenshots for UI, CI as the gate. Code tests use fixture packs; only the content tests read the shipped packs. | The agent host has no emulator. A vocabulary change must not break a code test. |
| D11 | Distribution is a signed APK built by CI, and F-Droid's public catalogue. Not Google Play. The repo therefore becomes public under a free licence before the first submission: nothing goes into the repo, its pull requests or its CI logs that should not be read by anyone. Every dependency is free software fetched from Google's Maven repository or Maven Central, and the repo holds no prebuilt libraries. The standard Gradle wrapper jar is the one exception. Native code that arrives inside a library from those two repositories, as Compose's does, is a dependency and not a file in the repo. | The owner's choice. The rules are what F-Droid's catalogue demands. Keeping to them from the first commit costs nothing; meeting them later means replacing libraries and rewriting history. |

## 4. Content model

Everything under `app/src/main/assets/content/`.

`grades.json` is the only place that maps a class to a level. It lists all twelve classes from the first commit and is not edited when a pack is added: a class whose pack file is absent shows as "coming soon".

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
        { "id": "dog", "text": "dog", "pos": "noun", "emoji": "🐶", "example": "A {dog} can bark." },
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

`avatars.json` lists the emoji a child can choose as an avatar. `pictures/<HEX>.png` holds one image per emoji used by any word, unit or avatar, named as OpenMoji names it. `pictures.json`, written by the sync script, maps each emoji to its file; the app reads it instead of rebuilding file names, and rule 9 checks it too. `audio/<word id>.ogg` holds one spoken clip for every band A word.

### Rules

Checked by one pure function in package `content`. The app's loader calls it at start and does not offer a pack that fails; a unit test calls the same function on every shipped pack, so a broken pack cannot be merged.

1. A word `id` and a unit `id` are lowercase kebab-case, and each is unique across all packs. Progress is stored against them, so from the first release they are never renamed or reused (see "Release bookkeeping").
2. Band A (P1–P2): every word has an `emoji`. Bands B and C: a word has an `emoji`, or both a `definition` and an `example`. Band D (S4–S7): every word has both a `definition` and an `example`, because band D has no picture exercises; a picture may be added but is never the only way to practise the word.
3. `example` contains exactly one pair of braces and no other brace. The text inside is spelled exactly as `text`. The loader parses it once into before, word and after; display and gap-fill both use that parsed form. Authors therefore use the word in its base form and not as the first word of the sentence.
4. `definition` does not contain the word as a whole word or phrase, compared without case. A substring test is wrong: it would reject "the" for containing "he".
5. Within a pack no two words share a `text` (compared without case and outer spaces) and no two words share an `emoji` (compared with the variation selector U+FE0F removed). Otherwise picture questions have two right answers.
6. A unit has 6 to 12 words. `text` is at most 24 characters.
7. `pos` is one of `noun`, `verb`, `adjective`, `adverb`, `phrase`, `other`.
8. Structure: `schemaVersion` is one the app supports; grade ids are unique; a pack's `grade` is the grade whose `pack` path points to it.
9. Every emoji of a word, a unit or an avatar has a file in `pictures/`, and `pictures/` holds no file that nothing uses.
10. Playability: the real generator can build every lesson of every unit, once with speech available and once without, and every word appears in at least one exercise both times. "Coming soon" is for a class with no pack, never for a pack that was merged but cannot be played.
11. Audio: every band A word has a clip in `audio/`, and `audio/` holds no clip without a word.

Two things no test can prove, so authors and reviewers check them for every word:

- The picture means the word to a child of that age. Reviewers open the PNG.
- An example identifies its word. "The {dog} is big" fits any animal; "A {dog} can bark" does not.

There is deliberately one validator. A separate schema file would be a second contract that can drift from the code.

### Pictures

`scripts/sync-pictures` (added with the walking skeleton) reads every emoji the content uses, resolves it through OpenMoji's own index at the pinned tag `17.0.0`, copies the 618 px colour PNG into `pictures/` and writes `pictures.json`. It runs when content is written, not during the build; the PNGs are committed. Authors keep writing an emoji character in JSON and never handle file names.

The app shows the credit "Pictures: OpenMoji (openmoji.org), licence CC BY-SA 4.0" as plain text on an About screen from the first build, and ships the licence notice in the APK. The pictures are used unmodified. If one is ever redrawn, the changed image is published under the same licence.

### Audio

`scripts/make-audio` (added with the bundled audio issue) generates one clip per band A word with Piper and its British voice "Cori", both pinned by version and checksum, then trims the silence, evens out the loudness and encodes mono OGG Vorbis. Like the pictures, it runs when content is written, not during the build. The clips are committed; the voice model is not. Each word is synthesised in a fresh session with a fixed seed, so a clip depends only on its own text: adding or changing another word leaves it byte for byte the same. The 140 clips of P1 and P2 take 872,082 bytes.

The clips are synthetic and are supplied under CC BY 4.0. The app credits Piper, the voice and its training data on the About screen and ships the notice. No agent can listen, so the script checks every clip for silence, clipping and an implausible length, and the owner hears the clips in the app.

### Release bookkeeping

Starts with the first build installed on a child's device (the release issue), not before. `released-ids.json` records, for that release, every unit id with its word ids, plus a list of retired ids. A unit test then fails when a released id has disappeared without being listed as retired, when a retired id is used again, or when a released word has moved to another unit.

Changing the word list of a released unit can change which words sit in which lesson. Stars stay with the lesson's position; per-word progress is unaffected. That is accepted. New words for a released class go into a new unit where possible.

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

About 1,430 words in total as planned. As written, on `main` on 2026-11-12: 1,434 words in 157 units (P1 61, P2 79, P3 103, P4 99, P5 114, S1 120, S2 120, S3 140, S4 147, S5 148, S6 151, S7 152), with 308 pictures (3.6 MB) and 140 clips (0.87 MB). The anchors (A2 at P5, B1 at S3, B2 at S5, C1 at S7) are the European Schools L2 targets; the years in between are my estimates. The levels say which vocabulary a class practises. They are not a certificate, and 150 words do not make a C1 speaker; the app shows the class label to the child, not the level. If the children follow a gentler curriculum, the fix is to shift the table, and a child can always pick a lower or higher class in the app.

## 6. Game model

Structure: class → units (themes) → words. A unit's words are split in order into introduction lessons of three or four words (6 → 3+3, 7 → 4+3, 8 → 4+4, 9 → 3+3+3, 10 → 4+3+3, 11 → 4+4+3, 12 → 4+4+4), followed by one review lesson over the whole unit. A lesson is about 10 exercises and takes two to three minutes. A lesson is identified by its unit id and its number within the unit.

Exercise types:

| Type | The child | Needs |
|---|---|---|
| Picture to word | sees a picture, picks the word | picture |
| Word to picture | reads or hears a word, picks the picture | picture |
| Listen and pick | hears a word, picks the picture or the written word | speech |
| Match pairs | pairs four or five words with their pictures or definitions | picture or definition |
| Spell with tiles | arranges letter tiles | picture or speech; single word of at most 10 letters |
| Definition to word | reads a definition, picks the word | definition |
| Gap-fill | picks the word that completes the sentence | example |
| Type the word | types the word from its definition, or as dictation: the word is spoken and its definition is shown, so that words which sound alike cannot be confused | definition; dictation also needs speech |

Five of the eight are a prompt with three or four options. They are one "choice" exercise with a kind of prompt and a kind of option, so a new choice type is data and a renderer, not a new screen.

Bands decide the mix:

| Band | Classes | Mix |
|---|---|---|
| A | P1–P2 | Picture and listening types only, three choices, the word is spoken automatically. Screens work without reading: pictures and icons carry the controls. Every word has a bundled clip, so a child who cannot read yet succeeds on any device (see "Speech"). |
| B | P3–P5 | Adds spelling tiles. A word without a picture is practised with gap-fill and definition to word; a word with a picture also gets gap-fill when it has an example. Four choices. |
| C | S1–S3 | Definitions and typing become regular. Fewer picture questions. |
| D | S4–S7 | Definitions, gap-fill, typing, and pairs of word and definition. No picture questions. About one typing question in three is dictation when the word can be spoken; the same holds in band C. |

Rules:

- **Capability filter.** The generator only emits an exercise that can work: `supports(type, word, band, environment, pool)`. The environment says whether a word can be spoken (it can when it has a clip or the device has a voice) and whether pictures can be perceived (they cannot when a screen reader is on). The pool is the rest of the word's pack: a choice needs enough other words of the right kind to fill its options. A word left with no type is skipped at run time, and rule 10 in section 4 makes sure that no shipped word is in that position.
- **Distractors** come from the same pack, same part of speech where possible, and never share a picture or text with the answer. For gap-fill they come from other units of the pack where possible: words from another theme rarely fit the sentence, which is the cheapest protection against two right answers.
- **Mistakes** are re-queued at the end of the lesson, at most twice per word. Stars: three for no mistakes, two for one or two, one for finishing.
- **Spaced repetition** is a five-box Leitner scheme per profile and word. Boxes are due after 0, 1, 3, 7 and 21 days. A session gives each word one outcome, however often the word appeared: any wrong answer sends it to box 1, due today; all right moves it up one box, but only if it was due; all right when it was not due changes nothing. So replaying a lesson three times in an afternoon cannot push a word to box 4. A word with no history counts as box 1 and due, so a first lesson without mistakes puts it in box 2. The intervals are a reasonable starting point, not tuned values. Chosen over SM-2 because it has no tuning parameters and a child or parent can understand it.
- **Practice** serves up to ten due words, the longest overdue first. It works with a single due word: distractors come from that word's pack, never from the due list. Each word is exercised in the band of its own class, so a P2 word stays a picture question for a child who has moved to P4. Words whose id is no longer in the content are ignored. A practice session has one exercise per word.
- **Rewards**: stars per lesson, points (10 for finishing a lesson or a practice session, plus 5 for each star), a daily streak based on the device's local date, and a crown for a finished unit. No leaderboards, since those need a network.
- **Hints.** The app is in English only. Anything that explains a word to a child, now or in a later version, is an English explanation taken from the word's `definition` or `example`. There are no translations.
- **Units unlock in order** within a class. The first unit is open. A lesson opens when the lesson before it has a star, and a unit opens when every lesson of the unit before it has one. What a child has already played never locks again: a lesson or unit with a stored result stays open even if a later change to the content adds a lesson before it. An adult can open everything from the parent corner. Changing class is always allowed and keeps all progress.
- The generator takes a random seed, so every lesson can be reproduced in a test.

### Speech

Band A depends on speech: without it a P1 child has to read. So the words of band A do not depend on the device.

- Every band A word ships with a clip (section 4, "Audio"). The app plays it; no speech engine is involved.
- Any other word is spoken by the device's text-to-speech. The app picks the voice itself: English, installed, not needing the network. British first, any other English voice second. It never asks the system to download voice data.
- Whether a word can be spoken is asked per word: a word with a clip always can, another word can when such a voice exists. Where a word cannot be spoken, its speaker button is hidden and the capability filter drops the listening types for it. Children in bands B to D can read, so their lessons go on with the other types.
- If a clip or an utterance fails in the middle of a lesson, the current question shows its word as text and that source of speech counts as unavailable for the rest of the session. A child never faces a listening question that made no sound.
- Android 11 and later need a `<queries>` entry for the speech service, or no engine is visible to the app.
- Speech never answers the question for the child. Where the word is what the child is given (word to picture, listen and pick, tiles or typing from speech), it is spoken at the start and a speaker button repeats it. Where the word is what the child has to find (picture to word, definition to word, gap-fill, tiles or typing from a picture or a definition), there is no speaker button until the child has answered; then the word is spoken once with the feedback, right or wrong. Band A is not affected: while its words can be spoken it has no question whose answer is a written word.

The interface in `audio` is asked to say a word, not a string. That is what lets a clip stand in for the synthetic voice without touching the callers.

## 7. App structure

Single activity, Compose Navigation, ViewModels with one-way state, manual dependency wiring (no DI framework).

| Package | Holds | Android imports |
|---|---|---|
| `content` | pack model, loader, validation | assets only |
| `game` | exercise generator, lesson session state machine, scoring, Leitner scheduling | none |
| `data` | Room database, repositories | yes |
| `audio` | speech behind a small interface, with a fake for tests that can be silent, unavailable or failing | yes |
| `ui` | screens: profile, class picker, path, lesson, result, practice, parent corner | yes |

`game` having no Android imports is the rule that keeps tests fast and keeps the core portable.

**The lesson contract.** Everything that starts or finishes a lesson goes through two types in `game`. A request is either a unit lesson (unit id, lesson number) or a practice session (a list of word ids). A result carries a session id, the request, one outcome per word, and the stars. Screens and features that start lessons, such as Practice, build a request; they do not reach into the generator. New exercise types change what the generator emits, not these two types.

**Persistence.** Room tables: `profile`; `word_progress` (profile, word id, box, due day, correct and wrong counts; indexed by profile and due day); `lesson_result` (session id, profile, unit and lesson or neither for practice, stars, day; one row per finished session, best stars are a query); `daily_activity` (profile, day, points). Every table refers to `profile` with cascading delete, so deleting a profile leaves nothing behind. Saving a result writes the lesson row, the word progress and the day's points in one transaction, and a session id that is already stored is ignored, so restoring the result screen cannot award a lesson twice. A fifth table, `app_settings`, holds what an adult sets in the parent corner; it arrived with schema version 2 and an automatic migration from version 1. Days are local dates. `java.time` needs core library desugaring on Android 7, and the SQLite of that version has no upsert statement, so writes use Room's own insert and update.

Room schemas are exported to the repo from the first commit. The schema may change freely until the first build is installed on a child's device; from then on every schema change ships with a tested migration. Builds before that release are for trying the app out: their progress does not carry over.

**Manifest.** No platform permission: nothing that gives access to the network, storage, sensors or anything else on the device. One entry stays, the permission that `androidx.core` declares for the app itself (`<application id>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`). It protects the app's own broadcasts on Android 12 and earlier and grants nothing. Removing it would make any library that registers a private receiver crash on those versions, and our JVM tests would not see it. `android:allowBackup="false"` together with data-extraction rules that exclude everything from cloud backup and from device transfer. The `emoji2` initialiser is removed from the merged manifest. A test asserts all three on the merged manifest.

**Tests and content.** Generator, session and UI tests load small fixture packs from test resources. Only the validation, picture and playability tests read `assets/content/`. A pull request that only changes vocabulary can therefore fail only on the content rules. The tests never use the network: Gradle resolves Robolectric's Android runtimes like any other dependency and the tests run in Robolectric's offline mode. Before that, a rate limit at Maven Central failed 23 tests in one CI run. In a Compose UI test, background work must not resume the composition off the test thread: whatever loads or decodes takes its dispatcher as a parameter, and the tests pass one that stays on their thread. Without that, one test run in three failed at random after the primary exercise set arrived.

## 8. Options considered

**Stack**

| Option | For | Against |
|---|---|---|
| Kotlin + Compose (chosen) | Proven here end to end; direct access to speech APIs. | Android only. An iOS version would need a new UI. |
| Flutter | iOS and web from one codebase; good headless widget tests. | No precedent in this account and unproven on the agent host. The right choice if iOS becomes a near-term need. |
| Web app in a wrapper | Fastest to iterate, previewable in a browser. | Speech, audio and offline behaviour are less reliable in a WebView; weaker game feel. |
| Game engine (Godot, Unity) | Strong for animation-heavy games. | Wrong tool for a text and button quiz: text layout, accessibility and speech all get harder. |

**Pictures**

| Option | For | Against |
|---|---|---|
| OpenMoji PNG subset (chosen) | About 10.6 KB a picture: 0.4 MB for 40. Measured after the primary packs were written: 284 pictures for the 456 words of P1–P5, 3.3 MB. Same image on every device, in Robolectric screenshots and in a reviewer's file viewer. No conversion step: OpenMoji publishes PNGs. | A sync script to maintain. CC BY-SA 4.0: a credit in the app, and share-alike for any picture we alter. |
| Bundled emoji font through EmojiCompat (revision 1) | Nothing to sync; no attribution to display. | 9.4 MB compressed before the first word. Identical rendering needs replace-all configuration, and whether colour emoji render in Robolectric screenshots was never tested. |
| System emoji | Zero bytes. | Missing emoji on older Android show as empty boxes; every manufacturer draws them differently; the default compatibility layer queries Google Play services. |
| Commissioned art | Best result. | Real cost and delay. |

**Speech**: three options. Device text-to-speech only (revision 1): nothing to produce, but silent on a device without an English voice, which a public F-Droid listing makes likely. Clips for the 140 band A words and the device's voice for the rest (chosen): about 0.8 MB, the youngest children's lessons work anywhere, and the owner approved the voice from samples. Clips for all 1,430 words: not needed, because older children can read when a device is silent.

**Backend**: none (chosen) against a hosted backend for accounts and sync. A backend brings children's personal data, consent handling and running costs. Revisit only if progress must follow a child across devices or a teacher needs a dashboard.

## 9. Tradeoffs and exit paths

| Decision | What we give up | Exit path |
|---|---|---|
| Android native | iOS | Content JSON is stack-neutral, and `game` is pure Kotlin, portable to Kotlin Multiplatform. |
| No backend | Sync, remote content updates, usage data | Packs are versioned files, so they can later be downloaded; repositories sit behind interfaces. |
| OpenMoji pictures | Our own art direction; words with no emoji have no picture | The `emoji` field stays the key and one component draws pictures. Swapping in a font or other artwork touches that component and the sync script, not the content or the database. An optional `image` field per word is additive. |
| Clips for band A, device speech for the rest | One voice throughout, and sound for older classes on a device without a voice | Clips are per word and additive: `scripts/make-audio` can cover more classes with no content or database migration. |
| No backup, no device transfer | Progress is lost with the device, and does not follow a child to a new tablet | Allowing device-to-device transfer only is a change to the extraction rules; it still sends nothing to a cloud. |
| No Google Play | The largest store | Nothing in the design prevents it later. It would need target API 36, the Families declarations and a privacy policy in the Console. |
| Leitner | Fine-grained scheduling | Scheduling is one class in `game`; stored data (box, due day, counts) is enough to seed another algorithm. |

Rollback for the project as a whole: nothing here touches an existing system. Any PR can be reverted on its own.

## 10. Risks and how each would show up

| Risk | How it shows up | Mitigation |
|---|---|---|
| Vocabulary is too easy or too hard for a class | Children get everything right, or give up. With no analytics we only learn this from the children and parents. | The level table is data; a child can change class; review agents check each pack against its target level. |
| No offline English voice on a device | No sound in P3 and above: speaker buttons and listening questions disappear. P1 and P2 are not affected, their words are clips. | Accepted: those children can read. Clips can be extended to more classes. |
| A clip or a voice that sounds wrong | A word is mispronounced or cut short, or a question is silent. | Clips are checked by script for silence, clipping and length, and heard by the owner in the app, since no agent can listen. The device voice prefers installed British voices. A failed clip or utterance turns the question into text. |
| A picture is ambiguous | A child cannot tell what the picture means (a face for "cold"). | Authors use a picture only where the meaning is obvious, otherwise a definition. Reviewers open each PNG. |
| Gap-fill has two right answers | A child picks a word that fits and is marked wrong. | Examples must identify their word; distractors come from other units; reviewers check every example. Not provable by a test. |
| Progress lost on update | A child's stars vanish after an app update. | Exported Room schemas and migration tests from the first installed build; released ids are frozen by a test. |
| Progress lost with the device | A broken or replaced tablet starts from zero. | Accepted for v1 as the price of "nothing leaves the device" (section 9). |
| The tests do not run on the Android versions that matter | Something works in every test and fails on a real device. Robolectric is pinned to SDK 34 and brings its own SQLite, so neither Android 15 nor Android 7 behaviour is exercised. The reason recorded for the pin, that the SDK 35 runtime needs JDK 21, turned out to be wrong for the Robolectric version in use; running the tests at SDK 35 is to be tried with the layout work. | A smoke test on emulators for API 24 and API 35 in CI before the first release: create a profile, play a lesson, restart, stars remain. Part of the release issue. |
| Shared build host runs out of memory | Gradle builds are killed mid-run when several agents build at once. | `--no-daemon`, `-Xmx2g`, at most two build-heavy issues in parallel, reviewers rely on CI results. |
| CI minutes on a private repo | Workflows stop running when the monthly quota is used up. | Build on pull requests and on `main` only; emulator job is manual. |
| Two channels, two signing keys | A family that installed from F-Droid cannot update from the CI APK, or the other way round: Android refuses an update signed with another key, and with backup off a reinstall loses the progress. | F-Droid's catalogue signs with its own key unless the build is reproducible. Either make the build reproducible so that F-Droid ships our signature, or say plainly in the README and on the About screen that a family stays on one channel. Settled in the release issue. |
| F-Droid's catalogue refuses or delays the app | No listing for weeks or months. Its policy expects three months of active development, which for this project means February 2027, unless the app has proven users by then. Acceptance is their decision. | The CI APK does not depend on it, and families using that APK are the proven users the policy asks for. An F-Droid repository of our own is the fallback. |
| Looks like a copy of an existing product | Takedown or rejection. | Own name, mascot, colours and content; mechanics only. Pictures are openly licensed and credited. |

## 11. Owner decisions and open questions

Answered by the owner on 2026-11-10 and 2026-11-11 (the numbers are those of the questions in revision 1 and on FRL-3):

1. Level targets: the European Schools L2 anchors.
2. Distribution: an APK from CI, and F-Droid's public catalogue if that is possible. It is: nothing in this design conflicts with their policy. Acceptance and timing are theirs.
3. English variety: British.
4. First language: none assumed. No translations; hints are explanations in English.
5. Name: "Word Quest" is already used by others. A new name is being chosen in its own issue. Until then it stays as the working title, and the release sets the final label and application id.
6. Speech for P1–P2: bundled clips in Piper's "Cori" voice, chosen by ear from samples. The eSpeak NG samples were rejected.
7. Pictures: OpenMoji for the first version.

Still open:

- **The licence.** The public catalogue needs one. Proposed: GPL-3.0-or-later for the code, CC BY-SA 4.0 for everything under `assets/content/` (our vocabulary, and OpenMoji's pictures, which already carry it). It is decided when the owner merges the licence files, which come in their own pull request.
- **When the repo becomes public.** At the latest before the F-Droid submission. Earlier is possible and makes CI minutes free. It is the owner's switch to flip, after a look at what becomes readable with it (the release issue lists that).

## 12. Revision 2: what the second opinion changed

Architect Astra reviewed revision 1 (commit `687f11e`) in FRL-4 and agreed with changes. Point by point:

| # | Point | Outcome |
|---|---|---|
| 1 | Stack | Unchanged. The claim "smallest APK" is removed: no Flutter build was ever measured. |
| 2 | Pictures | Accepted. A bundled OpenMoji PNG subset replaces the bundled emoji font (D4, sections 4 and 8). |
| 3 | Speech | Accepted, in two steps. Revision 2 took the explicit choice of an installed offline voice, the any-English fallback and the failure handling, and left bundled audio open. The owner then chose bundled clips for band A (D5), as the reviewer recommended, which made the planned notice to adults unnecessary. |
| 4 | Content contract | Accepted: stable unit ids, tighter rules 3 to 5, structural checks, the playability rule, released-id bookkeeping from the first release. Two alternatives chosen over the reviewer's: gap-fill ambiguity is handled by an authoring rule and other-unit distractors, not by hand-written distractor lists for a thousand words; and a changed lesson composition after release is a documented limit, not a mechanism. |
| 5 | Class-to-level table | Accepted: in-between years are labelled estimates, and levels are practice targets, not a claim of proficiency. |
| 6 | Game rules | Accepted: a filter that knows the band and the option pool, text exercises for every band B word, one Leitner outcome per word per session with promotion only when due, practice that works with one due word, a defined lesson split, and screen-reader handling through the same filter. Band A without sight is out of scope for v1. |
| 7 | Storage | Accepted: Room stays; one transaction per finished lesson, idempotent on session id, cascading deletes, an index on due day, exported schemas. |
| 8 | Staging | Accepted: `grades.json` complete and frozen from the walking skeleton, the lesson contract fixed there too, gap-fill and the definition fallback moved to the primary exercise set, release after profiles and accessibility. Recorded on FRL-3. |
| 9 | Google Play | Accepted: backup and device transfer off, no font request, privacy text inside the app. Target API 36 is recorded as a requirement of the Play path, not adopted for APK installs. The owner has since chosen F-Droid over Play (section 11). |
