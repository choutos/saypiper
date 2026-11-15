# SayPiper logo

The logo is an original blue speech bubble holding a white recorder pipe. The
refinement keeps the existing colours (`#3B5BA9`, white and `#FBF8F2`) and the
three finger holes. Rounder bubble corners, a shorter, gently rounded tail and
more space around the pipe make the mark calmer and clearer at launcher sizes.
The holes remain evenly spaced along the pipe's 30-degree angle.

## Editable assets

The Android vector XML files are the editable source; there is no bitmap master.

| Asset | Use |
|---|---|
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | Adaptive icon's colour mark |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | Same geometry with an even-odd pipe cutout for themed icons |
| `app/src/main/res/drawable/ic_launcher.xml` | Android 7 fallback; same paths at the existing 1.3 scale on a cream disc |
| `app/src/main/res/drawable/ic_launcher_background.xml` | Solid cream adaptive background |
| `app/src/main/res/drawable-anydpi-v26/ic_launcher.xml` | Adaptive layer references |
| `fastlane/metadata/android/en-GB/images/icon.png` | 512 × 512 store export |

Keep the three paths in the foreground, fallback and monochrome source in sync.
The monochrome version combines the paths into one even-odd fill: the pipe is
transparent and its three holes are filled. The store export uses the central
72dp of the 108dp foreground on a square cream background, matching the adaptive
icon's framing. The fallback keeps its existing, slightly smaller framing.

The manifest points to `@drawable/ic_launcher`, so the system splash on Android
12 and newer also receives this artwork. There is no separate splash drawable
or in-app logo use. See Android's [adaptive icon guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive)
and [default system splash behaviour](https://developer.android.com/develop/ui/views/launch/splash-screen/migrate).

## Export and verify

After setting up the toolchain as described in the README:

```sh
./gradlew --no-daemon testDebugUnitTest --tests '*LauncherIconTest' --rerun
cp app/build/outputs/launcher-icons/store-icon.png fastlane/metadata/android/en-GB/images/icon.png
```

`LauncherIconTest` renders the actual vectors with Android's native renderer in
Robolectric at SDK 35. It checks that every non-transparent foreground and
monochrome pixel fits inside the central 66dp safe circle, checks the themed
cutout and holes, and exports colour icons at 24, 32, 48 and 64px, the legacy
vector, and the store image to `app/build/outputs/launcher-icons/`. Run lint and
assembleDebug as well to check resource compilation.

These are JVM renders. Launcher-specific mask animations, the real system
splash and physical-device appearance still need a device or emulator check.

Rollback: revert the logo refinement commit, including the store PNG and the
matching vector paths. No application id, launcher component or stored data
changes are involved.
