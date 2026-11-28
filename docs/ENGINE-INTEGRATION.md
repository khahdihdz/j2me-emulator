# Engine integration — J2ME Loader (nikita36078/J2ME-Loader, tag 1.8.2)

All facts below were verified by direct reads of upstream files at tag
`1.8.2` (commit `7e978c5`) via GitHub raw / GitHub API on 2026-11-27.
Nothing here is guessed. Items marked UNKNOWN were not readable in this pass.

## 1. Upstream identity and license

- Repo: https://github.com/nikita36078/J2ME-Loader
- License: **Apache License 2.0**, Copyright 2017–2026 Nikita Shakarun
  (201-line LICENSE read in full at tag 1.8.2).
- Stable version: **1.8.2** (07/08/2024, commit `7e978c5`, versionCode 101).
  The `continuous` pre-release from master is rebuilt constantly and must
  NOT be pinned.
- Package: `ru.playsoftware.j2meloader`. Language: Java + C (native M3G).
- Supports `.jar`, `.jad`, `.kjx` (verified from AndroidManifest + installer).
- README system requirement: Android 5.0+ — contradicts `MIN_SDK = 14`
  in `app/build.gradle` (see §2).

## 2. Upstream toolchain — verified values (tag 1.8.2)

From root `build.gradle` (`ext` block):

| Item | Value |
|---|---|
| AGP | 8.5.1 (`com.android.application` / `com.android.library`) |
| COMPILE_SDK | 34 |
| MIN_SDK | 14 |
| TARGET_SDK | 34 |
| NDK_VERSION | 22.1.7171670 |

From `gradle/wrapper/gradle-wrapper.properties`: **Gradle 8.7**
(`gradle-8.7-bin.zip`).

From `gradle.properties`: `android.useAndroidX=true`,
`android.enableJetifier=false`, `org.gradle.jvmargs=-Xmx2048m`,
`android.defaults.buildfeatures.buildconfig=true`,
`android.enableR8.fullMode=false`.

From `settings.gradle`: `include ':app', ':dexlib'`, rootProject.name
`"J2ME Loader"`, repos: mavenLocal, gradlePluginPortal, google,
mavenCentral, jitpack.

From `app/build.gradle` dependencies (selection): Room 2.5.2
(`room-runtime`, `room-rxjava2`, `room-compiler`), appcompat 1.6.1,
material 1.9.0, preference 1.2.0, multidex 2.0.1, volley 1.2.1,
gson 2.9.0, guava 31.0.1-android, rxandroid 2.1.1, zip4j 2.10.0,
mididriver v1.21, pngj 2.2.3, mobile-ffmpeg v4.3.2-compact,
CameraX 1.3.0-beta01, filepicker 4.3.0, ambilwarna 2.0.1,
acra-dialog 5.9.3, kotlin-bom 1.8.0 (platform only), junit 4.13.2,
`implementation project(':dexlib')`. Flavours: `play`, `open`, `fdroid`,
`dev` (all `FULL_EMULATOR = true`), `midlet` (`FULL_EMULATOR = false`,
single-MIDlet APK). Native build: `externalNativeBuild.ndkBuild`,
path `src/main/cpp/Android.mk`.

**JDK: UNKNOWN from upstream files.** No JDK version is stated in any file
read. AGP 8.5.1 / Gradle 8.7 require JDK 17 to run — that is a toolchain
fact, not an upstream statement. Skeleton uses Java 17 in `app/build.gradle`
(`sourceCompatibility` / `jvmTarget = '17'`); CI must verify.

**`.gitmodules` at tag 1.8.2: 404 via GitHub raw** (file absent from that
tag's tree). On `master` it contains exactly one submodule:
`TinySoundFont`, path `app/src/main/cpp/mmapi_tsf/TinySoundFont`,
url `https://github.com/schellingb/TinySoundFont.git`. Before building
native code at 1.8.2, check which submodules (if any) that tag needs.

**Upstream CI workflow at 1.8.2: NONE — verified absent.** `get_file_contents`
on `.github/workflows` at ref `1.8.2` returns "path does not point to a
file or directory". There is no upstream CI to copy for tag 1.8.2, so the
JDK/SDK/NDK that upstream used to build 1.8.2 is UNKNOWN from upstream.
On `master` (commit `e8124df`, NOT 1.8.2) there is exactly one workflow,
`.github/workflows/release.yml` ("Automated CI builds"): checkout@v7 with
`submodules: true`, JDK **21** (setup-java@v6, Temurin), Gradle cache,
keystore + `keystore.properties` decoded from secrets `SIGNING_KEY` /
`KEYSTORE_PROPERTIES`, build task `./gradlew assembleDevRelease`, APKs
collected from `app/build/outputs/apk/dev/release/`. Those values describe
newer master code, not 1.8.2 — do not copy them blindly. Our pristine
workflow uses JDK 17 because our own skeleton CI (run #2, Success) proved
JDK 17 works with the same AGP 8.5.1 / Gradle 8.7.

## 3. Verified launch API

### `Config.startApp()` — the only entry point to run an installed game

File: `app/src/main/java/ru/playsoftware/j2meloader/config/Config.java`
(re-verified by direct read at tag 1.8.2 on 2026-11-27; the
`startApp` bodies below are the 1.8.2 text):

```java
public static void startApp(Context context, String name, String path, boolean showSettings)
public static void startApp(Context context, String name, String path,
                            boolean showSettings, String arguments)
```

- `path` is **not** the original JAR path. It is the **converted game
  directory**: `<emulatorDir>/converted/<appDirName>`.
- If `showSettings == true` or no config file exists for the game, it opens
  `ConfigActivity` (`ACTION_EDIT`); otherwise it opens `MicroActivity`
  directly. Consequence: "auto-run after import" is blocked by the settings
  screen on first run unless the launcher creates a default config first.
- Verified constants (`util/Constants.java`, tag 1.8.2, read in full):
  `ACTION_EDIT = "config.edit"`,
  `KEY_MIDLET_NAME = "midletName"`,
  `KEY_START_ARGUMENTS = "startArguments"`,
  `KEY_APP_URI = "appUri"`, `KEY_CONFIG_PATH = "configPath"`,
  `ACTION_EDIT_PROFILE = "config.edit.profile"`.
- Verified path constants (from `Config.java`, Phase 1):
  `MIDLET_CONFIGS_DIR = "/configs/"`, `MIDLET_DATA_DIR = "/data/"`,
  `MIDLET_DEX_FILE = "/converted.dex"`, `MIDLET_RES_FILE = "/res.jar"`,
  `MIDLET_ICON_FILE = "/icon.png"`,
  `MIDLET_MANIFEST_FILE = "/converted.dex.conf"`,
  `MIDLET_KEY_LAYOUT_FILE = "/VirtualKeyboardLayout"`,
  `FS_DIR = "/fs/"`, `SHADERS_DIR = "/shaders/"`.
- Default emulator dir: `context.getExternalFilesDir(null)` (non-legacy);
  otherwise pref `PREF_EMULATOR_DIR` (`"emulator_dir"`).

### `MicroActivity` — the MIDlet runtime activity

- `javax.microedition.shell.MicroActivity extends AppCompatActivity`.
- Manifest (tag 1.8.2, verified): `android:exported="false"`,
  `android:process=":midlet"`,
  `configChanges="orientation|screenSize|keyboardHidden|screenLayout|smallestScreenSize|uiMode"`.
- Because `exported=false` and it runs in a separate process, an external
  launcher **cannot** start it directly — even inside the same app, go
  through `Config.startApp()` after the game is installed/converted.
- `onCreate()`: reads `KEY_MIDLET_NAME` extra and `intent.getData()` as
  the app path; builds `MicroLoader`; `!microLoader.init()` falls back to
  `Config.startApp(..., showSettings=true)`.
- `loadMIDlet()`: 0 MIDlets → `Exception("No MIDlets found")`; 1 MIDlet →
  `MidletThread.create(microLoader, className)`; several → chooser dialog.
- Orientation (verified values): `ORIENTATION_DEFAULT=0, AUTO=1,
  PORTRAIT=2, LANDSCAPE=3`; AUTO → `SCREEN_ORIENTATION_FULL_SENSOR`,
  PORTRAIT → `SENSOR_PORTRAIT`, LANDSCAPE → `SENSOR_LANDSCAPE`. Reuse
  this; do not rewrite orientation handling.

### Install / convert: `AppInstaller` (in-process JAR → DEX)

- `ru.woesss.j2me.installer.AppInstaller` — constructors are
  package-private; the launcher must go through `InstallerDialog` /
  `AppListModel`, not call the constructor from another package.
- `loadInfo()`: `http/https` → `downloadJad()`; `content:` →
  `FileUtils.getFileForUri()`; `.jad` → `Descriptor`, `getJarUrl()`,
  missing URL → `ConverterException("Jad not have " + MIDLET_JAR_URL)`;
  local JAD without a JAR → status `STATUS_NEED_JAD` (user must pick the
  accompanying JAR); `.kjx` → `parseKjx()`; otherwise JAR →
  `loadManifest()`. Statuses: `STATUS_OLDEST=-1, STATUS_EQUAL=0,
  STATUS_NEWEST=1, STATUS_NEW=2, STATUS_UNMATCHED=3, STATUS_NEED_JAD=4,
  STATUS_SUCCESS=5`.
- `install()`: creates `<target>/.tmp`, runs
  `com.android.dx.command.dexer.Main` in-process (`--no-optimize
  --core-library --output=<tmp>/converted.dex <srcJar>`), copies the
  original JAR to `res.jar`, extracts the icon to `icon.png`, writes the
  manifest `.conf`, renames `.tmp` to the target dir, stores an `AppItem`
  in Room (`J2ME-apps.db`, entity fields: `id`, `imagePath`, `title`,
  `author`, `version`, `path` unique).
- `MainActivity` (upstream, verified): only when `savedInstanceState ==
  null` and the intent has no `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` does
  it take `intent.getData()` into the app list; `onNewIntent()` with data
  shows `InstallerDialog`. This is the anti-double-launch guard the
  skeleton copies.

### Receiving files (upstream manifest, tag 1.8.2, verified)

`MainActivity` (`exported=true`, `launchMode="singleTask"`):
- `ACTION_VIEW`, schemes `file`/`content`, MIME `application/java`,
  `application/java-archive`, `application/x-java-archive`,
  `text/vnd.sun.j2me.app-descriptor`.
- `ACTION_VIEW` for JAD over `http`/`https`, MIME
  `text/vnd.sun.j2me.app-descriptor`.
- `ACTION_VIEW`, MIME `*/*`, schemes `file`/`content`, `pathPattern`
  for `*.jar/*.JAR/*.jad/*.JAD/*.kjx/*.KJX` (multiple patterns to work
  around Android's one-`.`-per-pattern limit).
- No `ACTION_SEND` filter in upstream — if the launcher needs share
  receiving, add it; do not assume upstream has it.

## 4. Integration plan (v0.1.0)

1. Build the **pristine** upstream engine first (no modifications) on CI
   via `.github/workflows/engine-pristine.yml` (see §6) to lock
   JDK/AGP/NDK/SDK. Only after that build passes, change
   `applicationId`/`namespace` to `com.khahdihdz.j2meemulator` and add the
   launcher UI.
2. Keep the engine's `app` module as the main module. DECIDED
   (2026-11-27, after reading the 1.8.2 sources): **namespace stays
   `ru.playsoftware.j2meloader`; only `applicationId` changes** to
   `com.khahdihdz.j2meemulator`. Engine sources import
   `ru.playsoftware.j2meloader.R` and `.BuildConfig` explicitly
   (`Config.java`, `MicroActivity.java`, `InstallerDialog.java`,
   `EmulatorApplication.java`) and unqualified `R` references in that
   package resolve there too, so renaming the namespace would break
   compilation of the engine. Providers use `${applicationId}`, so
   their authorities follow the new applicationId automatically.
3. Launcher (this repo's code) calls the engine only through
   `InstallerDialog`/`AppListModel` for install and `Config.startApp()`
   for launch. It must create a default per-game config before first
   launch so auto-run after import is not blocked by `ConfigActivity`.
4. **Do NOT split the engine into a `:engine` library module in v0.1.0.**
   The engine is coupled to shared `R`, `BuildConfig.FULL_EMULATOR`,
   static `ContextHolder`, static `Config` dirs, Room DB, launcher
   resources/layouts, and native `.so` for 4 ABIs. Splitting it is a
   large, high-risk refactor with no benefit for v0.1.0.

## 6. Integration via Actions only (no local clone)

Chosen by VXNQR on 2026-11-27: engine integration happens only through
GitHub submodule declarations and GitHub Actions checkouts. No local
clone of J2ME Loader is read, run, copied, or built in this workspace.

- `.gitmodules` declares submodule `vendor/j2me-loader`
  (url `https://github.com/nikita36078/J2ME-Loader.git`, pin tag
  `1.8.2`), but the submodule **gitlink was never committed** — a
  gitlink cannot be created through the GitHub API or web UI, and
  `submodules: recursive` on our own checkout would therefore fetch
  nothing. `.gitmodules` is currently a declaration only, not a working
  submodule.
- `.github/workflows/engine-pristine.yml` works around that by checking
  out the upstream repo directly into `vendor/j2me-loader` with
  `actions/checkout@v4` (`repository: nikita36078/J2ME-Loader`,
  `ref: "1.8.2"`, `path: vendor/j2me-loader`). Trigger:
  `workflow_dispatch` + push touching that workflow file only, so it
  does not run on every ordinary push.
- Pristine build task: `./gradlew assembleFdroidDebug` in
  `vendor/j2me-loader`. Flavour `fdroid` and build type `debug` are both
  verified in upstream `app/build.gradle` at 1.8.2; `fdroid` is a full
  emulator flavour (`FULL_EMULATOR=true`) with no Play signing needs.
  The `midlet` flavour is NOT usable for a pristine build (it needs a
  MIDlet manifest). Upstream master builds `assembleDevRelease`, but
  that is a release build needing `keystore.properties` from secrets.
- NDK: upstream sets `ndkVersion 22.1.7171670` and builds native M3G via
  `ndkBuild` (`src/main/cpp/Android.mk`), so that exact NDK is required.
  The workflow installs it with
  `$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --install
  "ndk;22.1.7171670"` (syntax verified from Android sdkmanager docs /
  actions/runner-images guidance). NOT verified whether Google still
  serves NDK r22b through sdkmanager in 2026 — if the step fails with
  "package not found", that is the result to record.
- `tools/check-engine-pristine.py` (read-only, stdlib only) runs in the
  workflow before the build: it checks the required engine files/dirs
  exist in `vendor/j2me-loader` and prints the toolchain values and
  `Constants` values read from the engine's own files. Exit 1 on any
  missing required path.
- KNOWN RISK, CONFIRMED by pristine run #1 — `keystore.properties`:
  upstream `app/build.gradle` `signingConfigs.release` eagerly loads
  `keystore.properties` via `FileInputStream` unless `BITRISE_IO` is
  set. Pristine run #1 failed at Gradle configuration with
  `FileNotFoundException` for that file (app/build.gradle line 32).
  The fix, used in both `engine-pristine.yml` (run #2, Success) and
  `ci.yml`, is a step that writes a dummy `keystore.properties`
  (all values `dummy`, no real key) into the engine checkout / repo
  root before Gradle configures. Debug builds do not sign with it.
- After the pristine build passes, the merge is done by
  `tools/merge-engine.py` (written 2026-11-27, see §7), which runs on
  GitHub Actions inside `ci.yml` after the upstream checkout. It folds
  the engine source into this repo's `app` module (keeping `dexlib`,
  native code, assets and manifests) and generates the merged
  `app/build.gradle`, `AndroidManifest.xml` and `settings.gradle`.
  No engine files are committed to this repo.

## 5. Risks / open items

- Native M3G + 4 ABIs need the right NDK (`22.1.7171670` per upstream)
  and `Android.mk`; dropping native code to "build faster" will crash
  3D games.
- Upstream storage model is legacy (`requestLegacyExternalStorage`,
  `preserveLegacyExternalStorage`, `WRITE_EXTERNAL_STORAGE`). On
  Android 11+ (scoped storage) the launcher must use `getExternalFilesDir`
  + SAF and re-check `FileUtils.isExternalStorageLegacy()` /
  `DocumentProvider`.
- JAD over `http` downloads a JAR from the network (`downloadJad()`) —
  keep it behind a user confirmation, per the master prompt.
- Sub-licenses inside the engine (Nokia M3G engine, FFmpeg, keypad font)
  were flagged in Phase 1 from a secondary source only — check the
  license headers in `app/src/main/cpp` and `assets` directly before
  release, and keep every NOTICE.
- `Config.java`, `MicroActivity.java` (head + manifest entry),
  `AppInstaller.java` (head), `InstallerDialog.java` (full),
  `AppsListFragment.java` (install entry points), `MainActivity.java`
  (full), `Constants.java` (full), `EmulatorApplication.java` (head)
  and `NotificationBroadcastReceiver.java` (existence) were all
  re-read at tag 1.8.2 on 2026-11-27. NOT yet fully re-read at 1.8.2:
  `MicroLoader.java` (fetched; only the class declaration verified)
  and `Descriptor.java` (not re-read); the install flow below the
  `InstallerDialog` layer still rests on the Phase 1 read for those
  two files.

## 7. Merge on CI — tools/merge-engine.py (written 2026-11-27)

The merge runs on GitHub Actions inside `.github/workflows/ci.yml`,
after the upstream 1.8.2 checkout and NDK install. No engine file is
committed to this repo; the merged tree exists only in the CI runner.
`tools/merge-engine.py` (Python stdlib, no network) does, in order:

1. Reuses `tools/check-engine-pristine.py` to verify the checkout.
2. Copies engine Java trees `ru/`, `javax/`, `org/`, `com/` from
   `app/src/main/java/` into our `app/src/main/java/` (plus
   `app/src/androidTest` / `app/src/test` if the tag has them).
3. Copies engine `res/`, `assets/`, `cpp/` into `app/src/main/`.
   Values files are merged by resource name (engine wins on a clash).
   Verified: there is NO name clash between skeleton and engine for
   strings (249 engine vs 5 skeleton), colors (13 vs 7) or styles
   (13 engine vs 1 skeleton `Theme.J2MEEmulator`). One special case:
   the skeleton's `<string name="app_name">` is DROPPED — the engine
   generates `app_name` with `resValue` from `rootProject.name`, and a
   second XML definition would be a duplicate resource.
4. Copies engine `app/*.pro` files and the whole `dexlib/` module to
   the repo root (`dexlib/build.gradle` verified at 1.8.2: library
   module, namespace `com.android.dx`).
5. Generates `app/build.gradle` from the engine's file by text
   replacement with assertions (fails loudly if a pattern is absent):
   adds the `org.jetbrains.kotlin.android` plugin; KEEPS namespace
   `ru.playsoftware.j2meloader` (see §4 step 2); `applicationId` →
   `com.khahdihdz.j2meemulator`; `versionCode`/`versionName` from
   `APP_VERSION_CODE` / `APP_VERSION_NAME` env (defaults 1 / "0.1.0");
   adds `compileOptions` / `kotlinOptions` at Java **1.8** — deliberate,
   it matches the effective default the pristine engine build used;
   the skeleton used Java 17, do not raise this without rebuilding the
   engine sources against it first. Everything else (signingConfigs,
   all five flavours play/open/fdroid/dev/midlet, bundle splits,
   ndkBuild, `applicationVariants`, viewBinding, full dependency list)
   is kept verbatim. Flavours kept, not dropped: `fdroid` is the CI
   build flavour; `dev` calls `generateVersionCode()` (`git rev-list
   HEAD --count`, works in a CI checkout); `midlet` only reads
   `src/midlet/.../MANIFEST.MF` if that file exists.
6. Generates `AndroidManifest.xml` from the engine's manifest: the
   four intent-filters are MOVED from the engine `.MainActivity` to
   our forwarder activity (see §8); everything else (application
   attrs, ConfigActivity, MicroActivity, Settings/KeyMapper/FilePicker/
   Profiles activities, both providers, the Nokia receiver, all 14
   permissions) is kept verbatim. Generates `settings.gradle` with
   `rootProject.name = "J2ME Emulator"`, `include ':app', ':dexlib'`.
7. Final checks (exit 1 on failure): all required engine classes
   present (including `NotificationBroadcastReceiver`, referenced by
   the manifest); merged `build.gradle` contains the new
   `applicationId`, kept namespace, env versions and kotlin plugin;
   `settings.gradle` includes `:dexlib`; merged manifest has exactly
   one MAIN/LAUNCHER (the forwarder), VIEW filters only on the
   forwarder, and MicroActivity `exported=false` in process `:midlet`.

Tested on 2026-11-27 against a mock engine tree built from real
1.8.2 files fetched via the GitHub API (11 Java files, real manifest,
real build.gradle, real strings/colors): script exits 0, the merged
manifest/build.gradle/strings pass the checks above (253 strings =
249 engine + 4 skeleton, `app_name` absent; 20 colors = 13 + 7), and
re-running the script produces byte-identical outputs (idempotent).
That test used a PARTIAL tree (no `org/` sources, no assets, no
`androidTest`), so it proves the script's logic, not the real build.
The real build is proven only by the CI run after this merge lands.

## 8. Launcher after the merge

- Sole launcher: `com.khahdihdz.j2meemulator.MainActivity` — a thin
  forwarder (`android.app.Activity`, `Theme.NoDisplay`, finishes in
  `onCreate`). It owns MAIN/LAUNCHER and all four ACTION_VIEW
  intent-filters. It forwards `intent.data` (guarded by the same
  `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY` check as the engine) to the
  engine activity with an explicit `Intent`, then finishes. It calls
  no other engine API and draws no UI.
- Real launcher: `ru.playsoftware.j2meloader.MainActivity` (engine,
  kept, but its MAIN/LAUNCHER and VIEW filters are stripped by the
  merge). Verified flow at 1.8.2: cold start → `AppsListFragment.
  newInstance(uri)` → `onDbUpdated` shows `InstallerDialog.
  newInstance(uri)`; warm start → `onNewIntent` shows the same
  dialog; install success → `Config.startApp(context, title,
  pathExt, false)` → `ConfigActivity` on first run, `MicroActivity`
  afterwards. The forwarder does NOT call `InstallerDialog` or
  `Config.startApp` itself — those belong to the engine activity's
  lifecycle, and calling them from a `Theme.NoDisplay` activity would
  be wrong.
- Branding note: the merged app uses the engine's `@mipmap/ic_launcher`
  and `@style/AppTheme` (dark `DayNight` theme) because the whole
  engine `res/` tree is copied. The skeleton's light blue theme
  (`Theme.J2MEEmulator`) survives the merge unused. Replacing the icon
  and theme with our own branding is Phase 3 work, not merge work.
- Debug label note: the engine's `applicationVariants` block sets
  `app_name` to `'JL-Debug'` for debug builds of non-midlet flavours,
  so the merged debug APK is labelled "JL-Debug". Release builds get
  `app_name` from `rootProject.name` ("J2ME Emulator").

## 9. What can still fail on CI (unverified until the merged run)

- The full engine source tree is hundreds of Java files; the mock test
  covered 11. A missing `org/` or `com/` subtree in the real checkout
  would fail compilation — the script only WARNS on their absence.
  (The pristine build proved the full tree builds as upstream, so the
  risk is a checkout/copy problem, not engine code.)
- Engine unit tests, if the tag has any under `app/src/test`, run as
  `testFdroidDebugUnitTest`; their status at 1.8.2 was never checked.
- `lintFdroidDebug` may surface new lint findings from engine code;
  the engine's own lint block only disables `MissingTranslation` and
  `ExtraTranslation`. **Confirmed in CI run #12** (2026-11-27,
  https://github.com/khahdihdz/j2me-emulator/actions/runs/45984349361):
  27 errors, 168 warnings, after merge and unit tests passed. All 27
  errors are pre-existing upstream code (upstream tag 1.8.2 has no CI
  and never ran lint): MissingPermission 2 (LocalDevice.java:161
  getAddress, LocationProviderImpl.java:155 removeUpdates), NewApi 4
  (DiscoveryAgent.java:237/297 fetchUuidsWithSdp API 15,
  XmlUtils.java:609/615 ArrayMap API 19, minSdk was 14),
  UseAppTint 2 (listitem_checkable.xml:23, listitem_dir.xml:23),
  StringFormatMatches 19 (MainActivity.java:163 passes 2 args to
  alert_msg_workdir_not_exists which has 1 %1$s placeholder, plus
  every locale's definition of that string and err_missing_app).
  Fixes in tools/merge-engine.py (Step 8, each asserted): pass 1 arg
  at the MainActivity call site, android:tint -> app:tint in both
  layouts (xmlns:app already declared), minSdk 14 -> 19 in the merged
  build.gradle (upstream README says Android 5.0+), and a lint-only
  'MissingPermission' disable. No error was in our launcher code.
  The lint-report artifact of run #12 contained only the HTML report;
  the .txt path in ci.yml was corrected to
  app/build/reports/lint-results-fdroidDebug.txt.
- **CI run #15** (https://github.com/khahdihdz/j2me-emulator/actions/runs/45988963639,
  commits b8114c5 lint fixes, e0ae8d1 ci.yml, 7b5de51 docs): lint down
  to **2 errors, 196 warnings** — the other 25 fixed. The 2 left are
  the same StringFormatMatches root cause seen from the other side:
  values-ru/strings.xml:21 gives alert_msg_workdir_not_exists TWO
  placeholders (%1$s path, %2$s button label) while the base string
  and the other 17 locales have ONE, so the 1-arg call now fails for
  ru, and the midlet sample's com/example/kdlauncher/MainActivity.java:163
  (also 1 arg) fails the same way. Fix in the merge script: drop the
  ", нажав кнопку %2$s," clause from the Russian text (meaning kept;
  the button is still the dialog's neutral button). The other two
  %2$s uses in values-ru (reinstall_newest, reinstall_older) are
  untouched and verified to stay.
- **CI run #17** (https://github.com/khahdihdz/j2me-emulator/actions/runs/45992970306,
  commits 09bd55b lint fix, d22a082 docs): lint down to **1 error,
  195 warnings**. The last one is values-pt/strings.xml:23
  err_missing_app: "Verifique a% de disponibilidade" — the %s lost
  its 's', so lint parses "% d" (percent, space, 'd' of "de") against
  the base string's %s (values/strings.xml:37). The call site
  (ConfigActivity) passes one String, storageName, so the fix in the
  merge script is "a% de" -> "a%s de", matching base.
- The `dev` flavour's `generateVersionCode()` runs `git rev-list`;
  the CI build uses the `fdroid` flavour, so this is not on the
  critical path.
- Release builds (`assembleFdroidRelease`) still need a
  `keystore.properties` at Gradle configuration time; the CI dummy
  file lets configuration pass, but real release signing is Phase 9
  (apksign workflow signs the APK itself with apksigner — the Gradle
  `signingConfig` path is a separate, still-open question for then).
- The merged manifest is rewritten by `xml.etree.ElementTree`, so
  formatting and comments from the engine manifest are lost; only
  structure and values are preserved (verified by the final checks).

## 10. Phase 3 — Launcher: Home, Details, About (written 2026-11-27)

### Decision: option (a) extended — engine list stays the Library

The engine `MainActivity` + `AppsListFragment` already IS a complete
library screen (verified at tag 1.8.2): search (300 ms debounce),
sort by `PREF_APP_SORT`, FAB import via the engine file picker, context
menu (shortcut, rename, per-game settings, reinstall, delete), tap to
run. Rebuilding it would duplicate ~700 lines of verified engine
behaviour. Option (b) — our own LibraryActivity reading the Room DB —
was rejected for two concrete reasons, both verified in source:

1. **Two Room instances go stale.** `AppRepository` is only reachable
   through `AppListModel` (`ViewModelProvider(activity).get(...)`,
   verified in engine `MainActivity` and `AppsListFragment`), and each
   `AppListModel` builds its own `AppRepository` + Room instance.
   Room does not propagate writes between two instances in the same
   process (the engine does not enable multi-instance invalidation),
   so a list owned by our activity would go stale after the engine
   MainActivity installs a game. Our Home reads through the same
   repository pattern but re-queries on resume (see below).
2. **First-run work-dir setup lives in the engine MainActivity**
   (`checkAndCreateDirs` / `alertCreateDir` / permission flow, verified
   in `MainActivity.java`). Duplicating it risks a first run that
   never creates the work directory.

### Screens (prompt mapping)

- **Splash + Home — our `MainActivity`** (was the Phase 2 forwarder).
  Light `Theme.J2MEEmulator`, white cards, blue primary. Header card:
  app name, subtitle, work directory (`Config.getEmulatorDir()`).
  Game list (read-only): icon via `AppUtils.getIconBitmap(item)`
  (verified public static, null when no icon), title, author •
  version. Tap -> `Config.startApp(title, pathExt, false)`.
  Long-press -> our `GameDetailsActivity`. Buttons: "Thư viện &
  Nhập game" -> engine `MainActivity`; "Cài đặt" -> engine
  `SettingsActivity`; "Giới thiệu" -> our `AboutActivity`.
  Game-file intents (ACTION_VIEW / ACTION_SEND with a Uri, not from
  history) are still forwarded immediately to the engine MainActivity
  — the import flow is unchanged from Phase 2.
- **Library / Import — engine `MainActivity`.** Import, search, sort,
  rename, delete, reinstall, shortcut all live there (see §10
  decision). No code of ours duplicates them in v0.1.0.
- **Details — our `GameDetailsActivity`.** Values arrive as plain
  string extras (title, author, version, pathExt). Icon decoded with
  `BitmapFactory` from `pathExt + Config.MIDLET_ICON_FILE`
  (`"/icon.png"`, verified constant). "Chạy" ->
  `Config.startApp(..., false)`; "Cài đặt game" ->
  `Config.startApp(..., true)` (verified: always opens the per-game
  `ConfigActivity`). Rename/delete stay in the engine list.
- **Settings — engine `SettingsActivity`.** Global settings are the
  engine's own `res/xml/preferences.xml` (verified): `pref_theme`,
  `pref_black_background`, `pref_actionbar_switch`,
  `pref_statusbar_switch`, `pref_wakelock_switch`,
  `pref_screenshot_switch`, `pref_vibration_switch` (default true),
  `emulator_dir`, `pref_default_settings` (profiles), experimental
  `micro3d_using_message`. No setting was invented by us. Per-game
  settings = engine `ConfigActivity` (see Details).
- **About — our `AboutActivity`.** App name, version from
  `ru.playsoftware.j2meloader.BuildConfig.VERSION_NAME` (verified
  pattern from engine `AboutDialogFragment`), engine credit "J2ME
  Loader 1.8.2 (Apache License 2.0)", repo links, short how-to in
  Vietnamese, "Mở thư viện" button.

### Verified engine API used by our code (tag 1.8.2)

- `Config.startApp(context, name, path, showSettings)` and the 5-arg
  overload with `arguments`; `Config.getEmulatorDir()`,
  `Config.getAppDir()`; constants `MIDLET_ICON_FILE="/icon.png"`,
  `MIDLET_RES_FILE="/res.jar"`, `MIDLET_CONFIG_FILE="/config.json"`.
  `startApp` opens `ConfigActivity` when `showSettings` is true or no
  per-game config file exists, else `MicroActivity` directly.
- `AppListModel extends AndroidViewModel`, `getAppRepository()`;
  `AppRepository.observeApps(LifecycleOwner, Observer<List<AppItem>>)`,
  `getAll()` -> `Flowable<List<AppItem>>`, `get(int id)`,
  `insert/update/delete`, `onWorkDirReady()`, `getSort()`.
  `AppDatabase` (Room, entity `AppItem`, version 1, file
  `<emulatorDir>/J2ME-apps.db`, table `apps`, unique index on `path`);
  `AppItemDao` (`getAll`, `get(name, vendor)`, `get(id)`, …).
- `AppItem`: `getId/getTitle/getAuthor/getVersion/getPath`,
  `getPathExt()` = `Config.getAppDir() + path`,
  `getImagePathExt()` = `Config.getAppDir() + imagePath`.
- `AppUtils.getIconBitmap(AppItem)` (public static, may return null),
  `AppUtils.deleteApp(AppItem)` (NOT used by our code in Phase 3).
- `ru.playsoftware.j2meloader.settings.SettingsActivity`,
  `ru.playsoftware.j2meloader.MainActivity`,
  `ru.playsoftware.j2meloader.info.AboutDialogFragment` (reference for
  the About content; our About is a separate activity).

### Staleness + first-run handling in our Home (read the code comments)

- `onResume` re-queries `appRepository.getAll().firstElement()` on
  `Schedulers.io()` / `AndroidSchedulers.mainThread()` whenever the
  work directory is ready, to catch writes made through the engine
  MainActivity's own repository instance.
- `AppRepository` only opens its database when the emulator dir is an
  existing writable directory (verified in its constructor). If it is
  not ready at `onCreate`, our repository stays database-less, so the
  list stays empty and the user is sent to the engine MainActivity
  (which runs the real first-run flow). If the dir becomes ready while
  Home is paused, `onResume` restarts Home so a fresh `AppListModel`
  builds a repository with a database.
- Our code uses `import ru.playsoftware.j2meloader.R` explicitly:
  the merged namespace stays `ru.playsoftware.j2meloader`, so there is
  no `com.khahdihdz.j2meemulator.R`. Same for `BuildConfig`.

### Merge-script changes for Phase 3

- `merged_manifest`: the Home (ex-forwarder) activity theme changed
  from `@android:style/Theme.NoDisplay` to
  `@style/Theme.J2MEEmulator`. Two new activities are declared:
  `GameDetailsActivity` and `AboutActivity` (exported=false, no intent
  filters, same theme). `Theme.J2MEEmulator` comes from the skeleton's
  `res/values/themes.xml`, which survives the merge because the engine
  has no `res/values/themes.xml` (verified at tag 1.8.2).
- New layout names (`activity_home`, `item_game`,
  `activity_game_details`, `activity_about`) and all new string names
  (`home_*`, `details_*`, `about_*`) were checked against the engine
  tree at tag 1.8.2: none exist there, so the merge cannot silently
  overwrite them.
- `final_checks` now also asserts the Home theme and the two new
  activity declarations.

### Not done in Phase 3 (honest gaps)

- No search/sort on our Home list; use the engine Library for that.
- No rename/delete/reinstall/shortcut in our Details screen; engine
  list context menu owns them (Phase 5: library management).
- No per-game settings UI of our own; engine `ConfigActivity` owns it.
- No launcher icon yet (same as skeleton); the engine's
  `R.mipmap.ic_launcher` is still used as the app icon.
- The engine Library keeps the engine's own (dark) theme in v0.1.0;
  only our screens use the light blue/white theme.
- Not verified on CI yet: this is static-checked only (see below).

### Checks run (static only — no JDK/Gradle/Android SDK locally)

- `python3 -m py_compile tools/merge-engine.py` OK.
- `merged_manifest()` exercised on a synthetic manifest with the exact
  engine structure: Home gets the 4 intent filters + light theme,
  engine MainActivity stripped of filters, Details/About declared
  exported=false; idempotent re-run of `apply_lint_fixes()` still OK.
- All 7 XML files parse with `xml.etree.ElementTree`; every `R.id`,
  `R.string`, `R.layout` referenced in Kotlin exists in our res;
  every engine API name used in Kotlin is in the verified list above;
  no TODO markers, no calls to unverified engine APIs
  (`InstallerDialog`, `AppDatabase.open`, `deleteApp` appear only in
  KDoc comments, never in code). Brace/paren balance OK.
- **Not built, not run.** The real proof is the next CI run on
  `feat/launcher-engine` (merge -> `testFdroidDebugUnitTest` ->
  `lintFdroidDebug` -> `assembleFdroidDebug`).

## 11. Phase 4 — Intent import: JAR / JAD / KJX (written 2026-11-27)

All engine behaviour below was verified by direct read of upstream
tag 1.8.2 (`InstallerDialog.java`, `AppInstaller.java`,
`Descriptor.java`, `AppsListFragment.java`, engine `MainActivity.java`,
`FileUtils.java`, engine `AndroidManifest.xml`). The engine owns the
whole install flow; Home only routes intents into it.

### Import flow, by entry point

| Entry | What happens (verified) |
|---|---|
| ACTION_VIEW file/content URI (.jar/.jad/.kjx) | System matches one of the 3 VIEW filters (MIME types `application/java`, `application/java-archive`, `application/x-java-archive`, `text/vnd.sun.j2me.app-descriptor`; or `*/*` + pathPattern `*.jar`/`*.jad`/`*.kjx`, case variants). Home validates (see below) and forwards the Uri to the engine MainActivity. Engine: cold start → `AppsListFragment.newInstance(uri)` → `onDbUpdated` shows `InstallerDialog` once the DB is ready; warm start → `onNewIntent` shows it directly. |
| ACTION_VIEW http/https URI (.jad link) | VIEW filter 2 (JAD MIME type, http/https). `AppInstaller.loadInfo` downloads the JAD itself (`downloadJad`, 15 s connect / 3 min read, follows redirects) before parsing it. |
| ACTION_SEND / SEND_MULTIPLE | **The engine has no SEND support at all** — no SEND filter in the manifest, and no code reads `EXTRA_STREAM`. Home extracts the Uri from `EXTRA_STREAM` (first item for SEND_MULTIPLE) and forwards it as a VIEW data Uri. The merged manifest adds one SEND filter on Home (mimeTypes java-archive/x-java-archive/java/j2me-descriptor). |
| SAF file picker ("Nhập game từ file" on Home) | `ActivityResultContracts.OpenDocument` (`*/*` — providers report .jar/.jad with unreliable MIME types, so filtering happens in our validation + the engine). Home takes a persistable read permission (best effort), validates, forwards. The engine Library's own FAB picker (`FileUtils.getFilePicker`, SAF on scoped storage, legacy picker otherwise) also still exists. |

### What the engine does with the file (AppInstaller.loadInfo)

- `content://`: `FileUtils.getFileForUri` copies the stream to the
  cache `installer/` dir and sniffs magic bytes: `PK` → `tmp.jar`,
  `KJX` → `tmp.kjx`, anything else → `tmp.jad`. So a content:// Uri
  with no file extension still works.
- `file://`: used directly if the file exists.
- `.jad`: parsed by `Descriptor` (UTF-8 BOM stripped, line
  continuations joined). Requires `MIDlet-Name`, `MIDlet-Vendor`,
  `MIDlet-Version`, plus `MIDlet-Jar-Size` (integer) and
  `MIDlet-Jar-URL` (non-empty) — otherwise `DescriptorException`
  ("Fail attribute ..."), shown by the dialog as a Toast error.
  Local JAD + relative jar URL: if the JAD came from content:// on
  scoped storage → `STATUS_NEED_JAD`, the dialog asks the user to
  pick the JAR with the file picker; otherwise the JAR is looked up
  next to the JAD (by URL name, else same basename + `.jar`) —
  missing → `ConverterException("Jar-file not found for url: ...")`;
  manifest != JAD → `STATUS_UNMATCHED`, the dialog asks "This app
  differs from that described in JAD. Ignore JAD and install
  anyway?".
- `.jar`: manifest (`META-INF/MANIFEST.MF`) is required, else
  `IOException("JAR not have META-INF/MANIFEST.MF")`; missing
  `MIDlet-Name`/`Vendor`/`Version` → `DescriptorException`.
- `.kjx`: `parseKjx` checks the `KJX` magic and extracts the
  embedded JAD + JAR to cache.
- Corrupt file (bad JAD, bad manifest, unreadable stream, empty
  file): `DescriptorException` / `IOException` / `ConverterException`
  → `InstallerDialog.onError` shows Toast `"Lỗi: <message>"` and
  dismisses. The engine is the reporter for all of these; Home does
  not duplicate it.
- Unicode names: `Descriptor` strips the BOM; the app directory name
  is the game name with illegal filename characters
  (`[/\\:*?"<>|]`) removed, plus a `_1`/`_2` suffix if the directory
  already exists (`generatePathName`).
- Duplicates (by MIDlet name + vendor): `STATUS_NEW` (install),
  `STATUS_EQUAL` (dialog "App already installed. Do you want to
  reinstall it? All data will be saved." + a RUN button that runs
  the installed copy without reinstalling), `STATUS_NEWEST` /
  `STATUS_OLDEST` (update / replace-older dialogs with both
  versions shown). Reinstall keeps the app id/title and moves the
  RMS + per-game config directories when the path changes.
- Install: JAR→DEX in-process (dexlib `Main`, `--no-optimize
  --core-library`), res JAR copied, icon unzipped, manifest written,
  `.tmp` dir renamed over the target dir, `AppItem` inserted in Room.

### JAD pointing at a remote JAR — consent (prompt requirement)

No JAR is ever downloaded without the user agreeing. When the JAD's
`MIDlet-Jar-URL` is remote (absolute http(s), or relative resolved
against a remote JAD), `srcJar` stays null and the dialog appends
`warn_install_from_net` ("File will be downloaded from Internet") to
the confirm message; the download (`downloadJar`, same timeouts as
above) only runs inside `install()`, i.e. after the user taps
Install. The only download that happens before any dialog is the JAD
file itself when the user deliberately opens a remote .jad link — the
JAD is metadata, and the tap on the link is the user's own action.

### Auto-run after a successful import (prompt requirement)

Upstream shows "Application successfully installed!" and turns the
Install button into a START button — the game only runs on tap. The
merge script (`apply_phase4_fixes`, Step 9) patches the merged
`InstallerDialog` so `STATUS_SUCCESS` calls
`Config.startApp(requireActivity(), title, pathExt, false)` and
dismisses at once. Deliberate, documented behaviour change vs
upstream; asserted in `final_checks` (patch marker must be present).

### What Home adds (our code, MainActivity.kt)

- Validation before forwarding, deliberately lenient: rejects only
  an unsupported scheme (not file/content/http/https) or a file
  extension that is present but not .jar/.jad/.kjx. A rejected intent
  shows our own Vietnamese error dialog (`import_error_title` +
  `import_error_bad_extension` / `import_error_bad_scheme`) and Home
  stays open; nothing is forwarded.
- `onNewIntent` handling. The merged manifest now gives Home the
  engine MainActivity's `launchMode="singleTask"` and `configChanges`
  (inherited in `merged_manifest`, asserted in `final_checks`), so a
  second file intent while Home is running arrives via `onNewIntent`
  instead of stacking a new Home instance. (Before Phase 4 the
  forwarder had no launchMode at all — standard mode.)
- URI-permission grant flags (`FLAG_GRANT_READ_URI_PERMISSION` and
  friends) are copied from the source intent when forwarding, so the
  engine can read a content:// Uri it did not receive directly.
- History guard kept: a Uri restored from history
  (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`) is not forwarded again,
  same as the engine MainActivity.

### Not done in Phase 4 (honest gaps)

- No auto-run for the duplicate `STATUS_EQUAL` RUN path — that is a
  user choice in the engine dialog (run the installed copy vs
  reinstall), kept as-is.
- No download progress UI of our own for a remote JAR; the engine
  dialog shows its converting progress.
- No handling of a JAD whose `MIDlet-Jar-URL` is a non-http(s)
  scheme (e.g. `ftp:`): `downloadJar` forces an `http` scheme for a
  scheme-less URL, and any other scheme fails in `install()` with
  `ConverterException("Can't download jar")` → error Toast.
- Not verified on CI yet: static-checked only (see below).

### Checks run (static only — no JDK/Gradle/Android SDK locally)

- `python3 -m py_compile tools/merge-engine.py` OK.
- `merged_manifest()` exercised on the real engine 1.8.2 manifest:
  Home gets launchMode singleTask + configChanges, 5 intent-filters
  (MAIN + 3 VIEW + 1 SEND), engine MainActivity stripped of filters.
- `apply_phase4_fixes()` exercised on the real 1.8.2
  `InstallerDialog.java`: patch applies, second run skips
  (idempotent), START-button block gone, `Config.startApp(
  requireActivity(), ...)` present.
- XML files parse (`xml.etree.ElementTree`); every `R.id`,
  `R.string`, `R.layout` referenced in the changed Kotlin exists in
  our res; every engine API name used in Kotlin is in the verified
  list above (`Config.startApp`, `Config.getEmulatorDir`,
  `AppListModel`, `AppRepository.observeApps`/`getAll`,
  `AppUtils.getIconBitmap`, `SettingsActivity`); no TODO markers, no
  calls to unverified engine APIs.
- **Not built, not run.** The real proof is the next CI run on
  `feat/launcher-engine` (merge -> `testFdroidDebugUnitTest` ->
  `lintFdroidDebug` -> `assembleFdroidDebug`).

## 12. Phase 5 — Library management: search, sort, delete, details (written 2026-11-27)

### What the engine already had (verified at tag 1.8.2)

Read directly from `AppsListFragment.java`, `AppsListAdapter.java`,
`AppRepository.java`, `AppItemDao.java`, `AppItem.java`,
`AppUtils.java`, `Constants.java`, `res/values/arrays.xml` and
`res/values/strings.xml` at 1.8.2.

| Feature | Engine (1.8.2) |
|---|---|
| Search | `SearchView` in the options menu; RxJava debounce 300 ms, lowercased, `distinctUntilChanged`; `AppsListAdapter.AppFilter` matches when the lowercased title **or** author contains the lowercased query; empty query shows everything. |
| Sort | `PREF_APP_SORT` int preference (`Constants.PREF_APP_SORT = "pref_app_sort"`); dialog over `R.array.pref_app_sort_entries` (name / date = install order = `id` / vendor) with `R.array.pref_app_sort_values` SQL fragments (`title … %1$s, author … ASC`, `id %1$s`, `author … %1$s, title … ASC`, where `%1$s` is `ASC`/`DESC`); re-selecting the active variant flips direction via bit `0x80000000`. `AppRepository` observes the preference and re-queries (`MutableSortSQLiteQuery`: `SELECT * FROM apps ORDER BY …`). |
| Rename | Context menu → `alertRename`: `EditText` dialog, `item.setTitle(title)` + `appRepository.update(item)` (Room `@Update`). |
| Reinstall | Context menu → `InstallerDialog.newInstance(appItem.getId())`, shown only when `pathExt + Config.MIDLET_RES_FILE` exists. |
| Shortcut | Context menu → `ShortcutManagerCompat.requestPinShortcut` with the game icon. |
| Delete | Context menu → `alertDelete`: confirmation dialog (`R.string.message_delete`), then `AppUtils.deleteApp(item)` + `appRepository.delete(item)`. |
| Details | None — the engine list row shows only title, author, version. Our `GameDetailsActivity` is the details screen. |
| DB | Room v1, `J2ME-apps.db` in the work dir, table `apps`, unique index on `path`; `AppItemDao.delete` is Room `@Delete` (matches on the `@PrimaryKey id`). `AppItem` fields: `id`, `title` (mutable), `author`, `version`, `path` (converted-folder name); `getPathExt() = Config.getAppDir() + path`. No size field. |

### Delete keeps the original game file (prompt requirement) — verified

`AppUtils.deleteApp(AppItem)` at 1.8.2 deletes exactly three
directories and nothing else:

1. `item.getPathExt()` — the converted game directory
   (`<emulatorDir>/converted/<path>`: DEX, res JAR, manifest, icon);
2. `Config.getDataDir() + item.getPath()` — the game's saved data (RMS);
3. `Config.getConfigsDir() + item.getPath()` — the game's settings.

`AppRepository.delete(item)` then removes only the Room row. Neither
call touches the original `.jar`/`.jad` file: the installer only ever
*read* it (copied into a cache directory and converted there, Phase 4),
so the source file stays wherever the user had it. Our delete in
`GameDetailsActivity` runs these same two calls, in the same order —
no separate delete path was needed, and no original-file deletion was
ever possible through them.

### What Phase 5 adds (our code)

- **Search on Home** (`MainActivity.kt`, `activity_home.xml`): a
  `SearchView` above the list, 300 ms debounce (same as the engine),
  same match rule (lowercased title or author contains the lowercased
  query). `GameAdapter` keeps the full list and a filtered view and
  re-applies the current query whenever new data arrives (like the
  engine adapter's `setItems`). When a search finds nothing, the empty
  view says so (`home_no_results`) instead of claiming no games are
  installed.
- **Sort on Home**: a "Sắp xếp" button opening a dialog over the
  engine's own `R.array.pref_app_sort_entries` with title
  `R.string.pref_app_sort_title`, current variant from
  `appRepository.sort`, the engine's direction-toggle rule, and writing
  the same `PREF_APP_SORT` preference. Both repositories observe that
  preference, so Home and the engine Library always sort identically;
  no sort state is duplicated in our code.
- **Details** (`GameDetailsActivity.kt`,
  `activity_game_details.xml`): folder name (`item.path`) and total
  size of the converted directory (walked on a background thread;
  AppItem has no size field, so this is our own computation).
- **Delete in Details**: confirmation dialog whose message states
  that saved data and settings go away but the original file is kept;
  then the engine's two delete calls above, with the item rebuilt from
  the extras including its database `id` (Room `@Delete` needs the
  primary key), plus a Toast confirming the original file was kept.
  The repository comes from a `ViewModelProvider` in Details, the same
  pattern Home uses.

### What stays in the engine Library (not duplicated)

Rename, reinstall and home-screen shortcut stay in the engine
MainActivity's context menu — they already exist there, work through
the engine's own repository instance, and duplicating them would
reintroduce the two-repository staleness problem (Phase 3, §10).

### Known race (honest gap)

`AppRepository.delete` is asynchronous (Completable on the IO
scheduler, no completion callback). Details finishes right after
calling it, and Home re-queries on resume; an activity transition
normally takes far longer than the single-row delete, but a very fast
return could in theory show the deleted game once more until the next
resume re-query. The engine's own delete has no such race only because
its fragment keeps observing the same repository instance.

### Not done in Phase 5 (honest gaps)

- No launcher icon yet.
- No rename/reinstall/shortcut on our screens (in the engine Library).
- No per-game size breakdown (only the converted-directory total).
- Not verified on CI yet: static-checked only (see below).

### Checks run (static only — no JDK/Gradle/Android SDK locally)

- `python3 -m py_compile tools/merge-engine.py` OK (script unchanged
  in Phase 5 — no new activities, no manifest changes).
- XML files parse (`xml.etree.ElementTree`); every `R.id`,
  `R.string`, `R.layout`, `R.array` referenced in the changed Kotlin
  exists in our res or in the engine's merged res
  (`pref_app_sort_entries`, `pref_app_sort_title` verified in the
  engine's `arrays.xml` / fragment source at 1.8.2).
- All 11 new string names checked against the engine's 249 strings
  at 1.8.2: no collisions.
- Every engine API name used in Kotlin is in the verified list:
  `Config.startApp`, `Config.getEmulatorDir`, `Config.MIDLET_ICON_FILE`,
  `AppListModel`, `AppRepository.observeApps`/`getAll`/`sort`/`delete`,
  `AppUtils.getIconBitmap`/`deleteApp`, `SettingsActivity`,
  `Constants.PREF_APP_SORT`, `AppItem` constructor/`setId`/`getId`/
  `getPath`. No TODO markers, no calls to unverified engine APIs.
- **Not built, not run.** The real proof is the next CI run on
  `feat/launcher-engine` (merge -> `testFdroidDebugUnitTest` ->
  `lintFdroidDebug` -> `assembleFdroidDebug`).

## 13. Phase 6 — Controls: orientation and virtual keyboard (written 2026-11-27)

### What the engine already had (verified at tag 1.8.2)

Read directly from `javax/microedition/shell/MicroActivity.java`
(719 lines), `MicroLoader.java`, `config/ProfileModel.java`,
`config/ProfilesManager.java`, `config/ConfigActivity.java`,
`javax/microedition/lcdui/keyboard/VirtualKeyboard.java` (1377 lines),
`util/Constants.java`, `res/xml/preferences.xml` and
`res/values/arrays.xml` at 1.8.2.

**Orientation — per game, no global setting.**

- The value lives in the per-game profile: `ProfileModel.orientation`,
  JSON name `"Orientation"`, edited in `ConfigActivity` via the
  `screenOrientationSelector` spinner (selected position = value;
  entries `R.array.PREF_ORIENTATION_ENTRIES` = Default / Auto /
  Portrait / Landscape). `MicroLoader.getOrientation()` returns
  `params.orientation`; `MicroActivity.setOrientation` maps it:
  0 Default → `SCREEN_ORIENTATION_UNSPECIFIED`, 1 Auto →
  `SCREEN_ORIENTATION_FULL_SENSOR`, 2 Portrait →
  `SCREEN_ORIENTATION_SENSOR_PORTRAIT`, 3 Landscape →
  `SCREEN_ORIENTATION_SENSOR_LANDSCAPE`. The constants are private to
  `MicroActivity` (lines 91–94); the profile int is the interface.
- `MicroActivity` declares
  `configChanges="orientation|screenSize|keyboardHidden|screenLayout|
  smallestScreenSize|uiMode"`, so rotating does not restart the game.
- In-game menu (`R.menu.midlet_displayable`): "Lock orientation"
  (`action_lock_orientation`, API 18+) toggles
  `SCREEN_ORIENTATION_LOCKED` and restores the per-game value on
  unlock; "IME keyboard" (`action_ime_keyboard`) toggles the system
  soft keyboard via `InputMethodManager`.
- Phone-style virtual-keyboard layouts force portrait:
  `MicroActivity.onCreate` sets `ORIENTATION_PORTRAIT` when
  `vk.isPhone()`; same in the unlock path and after a layout switch.
- There is **no global orientation preference**: `preferences.xml`
  (global Settings) has no orientation entry, and `Constants.java`
  has no orientation preference key. A global override would have to
  intercept `MicroActivity` in the `:midlet` process, which is
  engine-owned — not attempted in v0.1.0.

**Virtual keyboard — per game, full-featured.**

- Created in `MicroLoader.applyConfiguration()`: when
  `params.showKeyboard` is true, `ContextHolder.setVk(new
  VirtualKeyboard(params))`; otherwise `setVk(null)` (no keyboard at
  all). Default for a new profile is `showKeyboard = true`
  (`ProfileModel(File)` constructor, line 187).
- Keys (VirtualKeyboard keypad array): 0–9 (`Canvas.KEY_NUM0…9`),
  `*` / `#`, soft keys L/R (`KEY_SOFT_LEFT` / `KEY_SOFT_RIGHT`),
  D-pad with diagonals (8 `DualKey`/`VirtualKey` entries:
  up/left/right/down + 4 diagonals), FIRE, gaming keys A/B
  (`SE_KEY_SPECIAL_GAMING_A/B`), C (`Canvas.KEY_END`), D
  (`Canvas.KEY_SEND`), and a MENU key (`KeyMapper.KEY_OPTIONS_MENU`).
  Touch presses call `vibrate()` when `params.vkFeedback` is true
  (haptic feedback; the global gate is `PREF_VIBRATION` =
  `"pref_vibration_switch"`, default true, read in
  `MicroActivity.onCreate` via `ContextHolder.setVibration`).
- Layout variants (`vkType`, `ProfileModel` line 117): 0 Custom,
  1 Phone, 2 Phone+arrows, 3 Numbers+arrows, 4 Arrows+numbers,
  5 Numbers, 6 Arrows; labels `R.array.PREF_VK_TYPE_ENTRIES`
  (7 items). Custom layouts are saved per game to
  `<config dir>/VirtualKeyboardLayout`
  (`Config.MIDLET_KEY_LAYOUT_FILE`). Other per-game VK settings:
  `vkButtonShape` (default `ROUND_RECT_SHAPE`), `vkAlpha` (default
  64), `vkForceOpacity`, `vkHideDelay` (auto-hide after N ms, 0 =
  never), button/outline colors.
- In-game menu (only when a VK exists): edit layout (move keys,
  `LAYOUT_KEYS`), scale layout (`LAYOUT_SCALES`), finish edit +
  save-layout prompt (`pref_vk_save_alert`, with an option to save
  screen params for phone layouts), switch layout dialog (over
  `PREF_VK_TYPE_ENTRIES`; switching to a phone layout re-applies
  portrait, otherwise restores the per-game orientation), hide-buttons
  dialog (per-key visibility toggles).
- Physical keys: `KeyMapper.setKeyMapping(params)` in
  `applyConfiguration()` maps Android key codes to J2ME keys from the
  per-game profile (`keyCodeMap` / `keyMappings`); the editor is the
  engine's `KeyMapperActivity` (declared in the manifest, reached
  from the engine Settings). Not re-implemented.
- Global defaults for new games: the default profile
  (`PREF_DEFAULT_PROFILE = "default_profile"`, `Constants.java:30`;
  `pref_default_settings` → `ProfilesActivity` in global Settings).
  A new game's first `ConfigActivity` open starts from that profile's
  values; there is no separate global orientation/keyboard switch.

**Our launcher screens.** Home, Details and About declare no
`requestedOrientation` in the merged manifest, so they follow the
device sensor (free rotation = auto). The forwarder/Home inherits
the engine MainActivity's `configChanges` in the merge (Phase 4), so
rotating Home does not restart it or lose the game list.

### What Phase 6 adds (our code)

Only a read-only display in `GameDetailsActivity` — the engine
already implements everything the prompt asks for in-game, so no
control was rewritten:

- **Orientation row** (`details_orientation`): `ProfilesManager.
  loadConfig(new File(Config.getConfigsDir(), folderName))` gives the
  game's `ProfileModel`; the label is
  `R.array.PREF_ORIENTATION_ENTRIES[params.orientation]` (the
  engine's own labels, so they stay in sync with what
  `ConfigActivity` writes). `loadConfig` is `@Nullable` and returns
  null when the game has no config file yet (no `config.json` and no
  legacy `config.xml`), so null is shown as "chưa cấu hình"
  (`details_orientation_unknown`) instead of inventing a value.
- **Virtual keyboard row** (`details_keyboard`): "Bật — <layout>"
  from `params.showKeyboard` + `R.array.PREF_VK_TYPE_ENTRIES
  [params.vkType]`, or "Tắt". Out-of-range values (a hand-edited
  config) fall back to the "unknown" strings instead of indexing
  out of bounds. File IO + Gson parsing run on a background thread,
  like the folder-size computation in Phase 5; any exception shows
  the "unknown" strings.
- Editing stays in the engine `ConfigActivity` via the existing
  "Cài đặt game" button (`Config.startApp(..., true)`); the
  orientation spinner and the virtual-keyboard toggle/group live
  there (`ConfigActivity.loadParams` lines 562, 586–599;
  `saveParams` lines 630, 665, 673).

### Not done in Phase 6 (honest gaps)

- No global orientation override (no engine support; see above).
- No per-game orientation/keyboard editor of our own; the engine
  `ConfigActivity` owns it.
- No launcher icon yet (still the engine's `ic_launcher`).
- No on-device verification of the keyboard layouts or of the
  lock-orientation menu; the in-game behaviour is engine code, not
  ours, and was verified only by reading it.
- Not verified on CI yet: static-checked only (see below).

### Checks run (static only — no JDK/Gradle/Android SDK locally)

- `python3 -m py_compile tools/merge-engine.py` OK (script unchanged
  in Phase 6 — no new activities, no manifest changes).
- XML files parse (`xml.etree.ElementTree`); every `R.id`,
  `R.string`, `R.layout`, `R.array` referenced in the changed Kotlin
  exists in our res or in the engine's merged res
  (`PREF_ORIENTATION_ENTRIES` and `PREF_VK_TYPE_ENTRIES` verified in
  the engine's `arrays.xml` at 1.8.2; the 4 orientation entries and
  7 VK-type entries match the value ranges 0–3 and 0–6).
- All 6 new string names checked against the engine's strings at
  1.8.2: no collisions.
- Every engine API name used in Kotlin is in the verified list:
  `Config.getConfigsDir`, `ProfilesManager.loadConfig`,
  `ProfileModel.orientation` / `showKeyboard` / `vkType`. No TODO
  markers, no calls to unverified engine APIs, no `*/*` inside a
  block comment (Phase 4 lesson), brace/paren balance OK.
- **Not built, not run.** The real proof is the next CI run on
  `feat/launcher-engine` (merge -> `testFdroidDebugUnitTest` ->
  `lintFdroidDebug` -> `assembleFdroidDebug`).

## 14. Phase 7 — Tests (written 2026-11-28)

The prompt requires unit/integration tests for: import JAR/JAD,
JAD→JAR, missing manifest/MIDlet, corrupt file, duplicates,
`content://`, ACTION_VIEW/SEND, MIDlet launch, orientation, config
save/restore, library — and forbids skipping a failing test to get
an APK. What Phase 7 actually does, honestly: real JVM unit tests
for everything that can run on the JVM, a manual checklist for
everything that needs a device, and no fake tests.

### What runs on CI already

`testFdroidDebugUnitTest` already ran in CI before Phase 7 — with
zero tests in it, so it passed trivially. Upstream has no JVM unit
tests at all (no `app/src/test` at tag 1.8.2; only instrumented
tests under `app/src/androidTest/java/javax/microedition/`, which
the merge copies but CI never runs — they need a device/emulator,
and there is none on the runner). The only test dependency the
merged build has is the engine's own
`testImplementation 'junit:junit:4.13.2'`. No Robolectric, no
Mockito, no Truth — and none is added here: a new test framework
would be an unverified dependency version plus more Maven downloads
on a runner that already hit Maven Central HTTP 429 twice (runs
#19, #48). `ci.yml` now also uploads the JUnit XML/HTML results
as artifact `unit-test-results-fdroid-debug` (`if: always()`),
like the lint report, so a failure is inspectable without
re-running CI.

### Refactor first: pure rules, thin activities

The logic under test used to live inside `MainActivity` /
`GameDetailsActivity`, where it touches `Intent`, `Uri`,
resources and views and cannot run on the JVM. Phase 7 moves it
unchanged into `GameRules.kt` (`com.khahdihdz.j2meemulator`,
zero Android imports) and leaves the activities as thin adapters:

- `GameUriRules.validate(scheme, path)` — the lenient import
  pre-check (accepted schemes `file`/`content`/`http`/`https`;
  extension after the last dot, missing/empty accepted, unknown
  rejected). `MainActivity.validateGameUri` only maps the result
  to a string resource.
- `GameUriRules.pickIntentUri(action, data, stream, streams,
  fromHistory)` — which Uri an intent carries (VIEW → data,
  SEND → `EXTRA_STREAM`, SEND_MULTIPLE → first item, history
  ignored). The action values are the stable `Intent.ACTION_*`
  strings spelled out, since the constants live in Android.
  `MainActivity.gameUriFromIntent` only extracts the strings
  from the `Intent` and parses the result back with `Uri.parse`
  (a `toString()` → `parse()` round-trip).
- `LibraryRules.matches(title, author, query)` — the engine's
  own match rule (lowercased title or author contains the
  lowercased query; empty query matches; no trimming).
- `LibraryRules.toggleSort(current, picked)` — picking the
  active sort variant flips its direction bit (`Int.MIN_VALUE`).
- `FormatRules.formatSize` / `dirSize` / `orientationLabel` /
  `vkLayoutLabel` — moved unchanged from `GameDetailsActivity`
  (size formatting, recursive directory size, out-of-range label
  → null instead of an array-index crash).

### Tests written (all JVM unit tests, JUnit 4)

| File (`app/src/test/java/com/khahdihdz/j2meemulator/`) | Tests | Covers (prompt cases) |
|---|---|---|
| `GameUriRulesTest.kt` | 25 | Import pre-check: accepted schemes/extensions incl. uppercase; bad scheme (`ftp`, missing, empty); bad extension (`.apk`, `.zip`); lenient cases (no extension, trailing dot, null path — engine decides); intent Uri extraction: VIEW/SEND/SEND_MULTIPLE/first-item/history/unknown/null action |
| `LibraryRulesTest.kt` | 13 | Library search: empty query, title/author match, case-insensitivity incl. Vietnamese (`Cờ Tỷ Phú` / `TỶ PHÚ`), no trimming, no match; sort toggle: flip bit, select other variant, reset after a flipped value |
| `FormatRulesTest.kt` | 14 | Size formatting (B/KB/MB/GB boundaries, `Locale.US` pinned in the test because `%.2f` uses the default locale); `dirSize` on real temp dirs (empty, nested sum, missing dir → -1, file → -1); orientation/VK label in-range vs out-of-range → null |
| `DescriptorTest.kt` | 21 | Engine `ru.woesss.j2me.jar.Descriptor` (1.8.2): valid JAD and manifest parse, JAD from a real file, BOM strip, continuation lines, Jar-URL trim; missing MIDlet-Name/Vendor/Version, missing/non-integer Jar-Size, missing/empty Jar-URL, missing manifest Name; garbage and empty input; `compareVersion` ordering; `equals` on Name+Vendor (duplicate detection) |

Total: **73 tests**. Every test asserts the real behaviour,
including the failure paths (`expected = DescriptorException::class`).

### Why `Descriptor` works on the JVM — and the one build change

`Descriptor` is plain Java: its constructor only parses text.
It touches Android in exactly one place — `android.util.Log.e`
in the error path of `init()` — and unit-test Android stubs
throw `RuntimeException("Stub!")` when called. The merge script
therefore adds one block to the generated `app/build.gradle`
(`merged_build_gradle`, asserted in `final_checks`):

```groovy
testOptions {
    unitTests.returnDefaultValues = true
}
```

This makes stub calls no-ops (Log.e returns 0) so the real
`DescriptorException` propagates — which is exactly what the
corrupt-input tests assert. It affects unit tests only; the app
is unchanged. `getInfo(Context)` is deliberately NOT tested —
it needs a real `Context` and Android resources.

### Not covered by automated tests (honest gaps)

- **No integration tests on a device.** `AppInstaller`
  (JAR→DEX, Room insert, duplicate STATUS_* flow), `FileUtils.
  getFileForUri` magic-byte sniffing, `Config.startApp` launching
  a MIDlet, orientation actually rotating, config save/restore
  round-trip through `ConfigActivity`, and the Room-backed
  library all need Android. They are in the manual checklist
  below, not in fake passing tests.
- **No Robolectric / androidTest added.** See above (unverified
  dependency, no emulator in CI).
- **No release build in CI yet.** CI builds `assembleFdroidDebug`
  only; the release build with signing is Phase 9/10 (apksign).
  The prompt's "debug and release build" is therefore half done
  until then.

### Manual checklist (on-device, with the debug APK)

1. Import a valid `.jar` from Files (ACTION_VIEW) → installs
   and auto-runs.
2. Import a valid `.jad` (local JAR next to it) → installs.
3. Import a `.jad` whose JAR URL is remote → dialog warns
   about downloading; nothing downloads before tapping Install.
4. Import a `.jar` without `META-INF/MANIFEST.MF` → engine
   error toast, no crash.
5. Import a `.jad` missing `MIDlet-Name` → engine error toast.
6. Import a corrupt `.jar` (random bytes) → engine error toast.
7. Import the same game twice → reinstall/duplicate dialog
   (equal / newer / older by version).
8. Share a `.jar` from another app (ACTION_SEND) → installs.
9. Import via Home "Nhập game từ file" (SAF) with a
   `content://` Uri that has no extension → installs (magic
   sniffing).
10. Launch an installed game from Home and from Details → the
    MIDlet actually starts.
11. In Details, change orientation (Default/Auto/Portrait/
    Landscape) in "Cài đặt game", relaunch → the game rotates
    (or locks) as chosen; virtual keyboard shows with the
    chosen layout, D-pad / 0–9 / soft keys work.
12. In Details, delete a game → the converted folder and the
    Room row go away, the original `.jar`/`.jad` file stays.
13. Search and sort on Home match the engine Library.
14. Config save/restore: change a per-game setting, kill the
    app, reopen Details → the setting is still there.

### Checks run (static only — no JDK/Gradle/Android SDK locally)

- `merged_build_gradle()` run against the real upstream 1.8.2
  `app/build.gradle` text (fetched via GitHub API): all previous
  edits still apply, `testOptions` present, deterministic on
  fresh input. `py_compile` on the merge script OK.
- Every engine API name used in the tests is in the verified
  list: `Descriptor` / `DescriptorException`
  (`ru.woesss.j2me.jar`, constructors and getters verified in
  the 1.8.2 source). No TODO markers, no `*/*` inside a block
  comment (Phase 4 lesson), brace/paren/bracket balance OK in
  all 4 main-source Kotlin files and all 4 test files.
- Test expectations hand-checked against the engine source:
  `Descriptor.parse` (BOM strip, continuation join),
  `verifyJadAttrs` (Jar-Size integer, Jar-URL non-empty),
  `verify` (Name/Vendor/Version), `compareVersion`
  (missing part counts as 0), `equals` (Name+Vendor only).
- One real bug caught while writing the tests: the first draft
  of the `compareVersion` test double-negated the result of
  `compareVersion("1.0.1")`; fixed to the correct expectation
  (`-1`, "1.0" is older than "1.0.1") before any upload.
- **Not built, not run.** The real proof is the next CI run on
  `feat/launcher-engine` (merge -> `testFdroidDebugUnitTest`
  (73 tests) -> `lintFdroidDebug` -> `assembleFdroidDebug`).
  If any test fails there, the phase is NOT done — no test is
  skipped or deleted to get an APK.

## 15. Phase 9 — Signing with apksign (prepared 2026-11-28, NOT run yet)

The prompt requires release signing through
[khahdihdz/apksign](https://github.com/khahdihdz/apksign), one signing
identity across versions, secrets only via GitHub Actions Secrets.
What Phase 9 actually does: verify apksign again from source,
prepare `.github/workflows/release.yml`, and record the exact
mandatory order. Nothing has been signed or released yet.

### apksign verified again (head `a6f44ef`, 2026-11-03)

- MIT License, Copyright (c) 2026 khahdihdz. Termux tool managing
  one shared release keystore (RSA-4096, alias
  `khahdihdz-release`, local `~/.apksign/`, never in a repo).
- `sync-android-signing.yml`: triggers `workflow_dispatch` + cron
  `17 2 * * *`. It validates the central keystore first (Base64
  decode, `keytool -list` with the store password, alias check, and
  a key-password check via `keytool -importkeystore` to PKCS12),
  then lists the owner's repos (`gh repo list`, public + private)
  and, for each repo whose **default branch** tree contains a file
  ending in `src/main/AndroidManifest.xml`, sets the 4 secrets
  `ANDROID_SIGNING_KEYSTORE_BASE64` / `ANDROID_SIGNING_STORE_PASSWORD`
  / `ANDROID_SIGNING_KEY_ALIAS` / `ANDROID_SIGNING_KEY_PASSWORD`.
  It does NOT copy any workflow file. The sync runs daily — latest
  run #84 on 2026-11-27 — but it can only sync to a repo once that
  repo's default branch has the manifest.
- `android-shared-signing.yml` (`workflow_call`): inputs
  `project-dir` (default `"."`), `gradle-task` (default
  `"assembleRelease"`), `version-code` (default `"1"`),
  `version-name` (default `"0.1.0"`); secrets are the same 4 above,
  all required. It decodes the keystore, normalizes it to PKCS12
  (store password for both store and key), builds with
  `APP_VERSION_CODE` / `APP_VERSION_NAME` from the inputs,
  installs build-tools 35.0.0, zipaligns, signs with `apksigner`
  (`--ks-pass` and `--key-pass` both from the store password),
  verifies, and uploads artifact `android-signed-apks`. It does
  NOT create a GitHub Release.

### Why the release workflow does NOT call the shared workflow

The Phase 1 plan assumed a direct reusable-workflow call. Reading
both workflows in full shows four verified blockers, so
`.github/workflows/release.yml` does the merge and the signing
itself instead (same secrets, same PKCS12 normalization):

1. The shared workflow only checks out the calling repo. It never
   checks out upstream J2ME Loader, never installs the NDK, never
   runs `tools/merge-engine.py` — so the engine sources and the
   `:dexlib` module are missing and Gradle configuration fails.
   (The committed `settings.gradle` only includes `:app`;
   `:dexlib` is generated by the merge script at CI time.)
2. It never creates `keystore.properties`, which the merged
   `app/build.gradle` loads unconditionally at configuration time
   (the pristine engine run #1 failed exactly this way).
3. Its default gradle task `assembleRelease` does not exist once
   product flavours are added — ours is `assembleFdroidRelease`.
4. It looks for APKs under `*/build/outputs/apk/release/`, but a
   flavoured build lands in `app/build/outputs/apk/fdroid/release/`.
   It would find no APK and fail.

If the shared workflow ever gains a pre-build hook, calling it
becomes possible again; until then this is the working route. The
decision is the parent's to revisit — the prepared workflow is
ready either way.

### What `release.yml` does (18 steps, one job)

Triggers: `release: types: [published]` (tag must be `v0.1.0`,
checked against `APP_VERSION_NAME`) + `workflow_dispatch` for a
dry run (per the `android_pkcs12_signing` skill: a tag created
through the web UI does not trigger a tag-push workflow, and the
release must be runnable by manual dispatch).

1. Merge: checkout, JDK 17, upstream 1.8.2 checkout, NDK
   22.1.7171670, engine verify, `merge-engine.py` — the same
   steps as `ci.yml`.
2. Tests: `testFdroidDebugUnitTest` (73 tests) must pass; results
   uploaded even on failure.
3. Diagnose + normalize signing keystore (diagnostic step kept
   permanently, per the skill): the 4 secrets must be non-empty;
   the keystore is decoded (size + SHA-256 recorded), opened with
   the store password, its alias entry listed; then normalized to
   PKCS12 with the store password for both store and key (Java
   ignores a separate key password for PKCS12). Secret values are
   never printed. A real `keystore.properties` is written for the
   merged `app/build.gradle` — runner-only, never committed.
4. Build: `./gradlew assembleFdroidRelease` with
   `APP_VERSION_CODE=1`, `APP_VERSION_NAME=0.1.0`. Gradle signs the
   release buildType with `signingConfigs.release` (kept from
   upstream by the merge script; `minifyEnabled` + `shrinkResources`
   are upstream's own config). The fdroid flavour has no
   `versionNameSuffix`, so the APK is stamped exactly 1 / 0.1.0.
5. Verify: `apksigner verify --verbose --print-certs`, then
   `aapt2 dump badging` must show package
   `com.khahdihdz.j2meemulator`, versionCode `1`, versionName
   `0.1.0`.
6. Assets: the APK is copied to `J2ME-Emulator-signed.apk`,
   `SHA256SUMS.txt` is generated, both are uploaded as artifact
   `J2ME-Emulator-signed-apk` and — only on a real release event —
   attached to the GitHub Release with `gh release upload`
   (needs `permissions: contents: write`).

### Prerequisites — what is ready and what is missing

| Prerequisite | State |
|---|---|
| `feat/launcher-engine` head `af7803b`, CI run #60 Success, 73 tests pass | Ready |
| `main` branch | NOT ready: only the empty Initial commit `9df4848`; no `app/src/main/AndroidManifest.xml`, so the apksign sync skips this repo by its own logic |
| 4 `ANDROID_SIGNING_*` secrets on `j2me-emulator` | NOT present (follows from the row above: the sync only writes them when the default branch has the manifest; it has never had one). Secret names/values cannot be read via the API — confirm in Settings → Secrets after the sync runs |
| apksign sync workflow | Runs daily (latest #84, 2026-11-27) + manual dispatch; central secrets validated by the workflow itself |
| `release.yml` | Prepared locally, YAML-valid, 18 steps; NOT uploaded yet (workflow files go via GitHub web upload) |

### Mandatory order (nothing can be skipped)

1. **PR `feat/launcher-engine` → `main`, merge only when CI is
   green** (prompt rule). This puts `app/src/main/AndroidManifest.xml`
   on the default branch. Needs Blake's decision to create/merge
   the PR.
2. **Run the apksign sync** — Actions → Sync Android signing to
   all repositories → Run workflow in `khahdihdz/apksign`, or wait
   for the daily 02:17 UTC run. Then confirm the 4 secret names
   exist in `j2me-emulator` Settings → Secrets (values are never
   readable — that is by design).
3. **Upload `release.yml` via the GitHub web UI** to `main`
   (workflow files cannot go through the API push). Needs Blake's
   go-ahead, or Muse does it through the browser.
4. **Dry run first:** run the Release workflow by manual dispatch
   on `main`, check the signed APK artifact (name, size, SHA-256),
   `apksigner verify` output and the version check in the log.
   Only then create the real release.
5. **Create GitHub Release `v0.1.0`** (tag `v0.1.0`, from `main`)
   in the web UI — this triggers the workflow, which builds,
   signs, verifies and attaches `J2ME-Emulator-signed.apk` +
   `SHA256SUMS.txt` to that release. (Phase 10.)

Alternative if the merge to `main` must wait: Blake can set the 4
`ANDROID_SIGNING_*` secrets manually in the repo Settings from the
verified apksign credentials (Termux `apksign` menu 9 / menu 5
export) — but the values must never be pasted into chat, and the
manual route still needs `release.yml` on a branch to run from.
Not done here; only listed as an option.

### Checks run (static only — no JDK/Gradle/Android SDK locally)

- `release.yml` parses as YAML; triggers `release: published` +
  `workflow_dispatch`; `permissions: contents: write`; 18 steps in
  the order above; env `APP_VERSION_CODE: "1"`,
  `APP_VERSION_NAME: "0.1.0"`; tag check `v${APP_VERSION_NAME}`;
  APK path `app/build/outputs/apk/fdroid/release/`; asset names
  `J2ME-Emulator-signed.apk` + `SHA256SUMS.txt`.
- Every step that touches the build (upstream checkout ref
  `1.8.2`, NDK `22.1.7171670`, `check-engine-pristine.py`,
  `merge-engine.py`, `testFdroidDebugUnitTest`,
  `assembleFdroidRelease`) is copied from the green `ci.yml`.
- The keystore diagnostic/normalization block mirrors the apksign
  shared workflow's own commands (same `keytool -importkeystore`
  loop over JKS/PKCS12 × key-password/store-password, same
  PKCS12 output with the store password for both store and key).
- **Not built, not run, not signed, not released.** The first real
  proof is the manual-dispatch dry run after the PR merge and the
  sync (steps 1–4 above).
