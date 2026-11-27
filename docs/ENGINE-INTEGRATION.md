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
