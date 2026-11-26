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

**Upstream CI workflow at 1.8.2: UNKNOWN** — `.github/workflows/*` content
was not read in this pass.

## 3. Verified launch API

### `Config.startApp()` — the only entry point to run an installed game

File: `app/src/main/java/ru/playsoftware/j2meloader/config/Config.java`
(verified at an older commit `db0b9d6f` in Phase 1; must be re-diffed
against tag 1.8.2 before coding against it):

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

1. Checkout the submodule at tag 1.8.2:
   `git submodule update --init && git -C vendor/j2me-loader checkout 1.8.2`.
2. Build the **pristine** upstream engine first (no modifications) on CI
   to lock JDK/AGP/NDK/SDK. Only after that build passes, change
   `applicationId`/`namespace` to `com.khahdihdz.j2meemulator` and add the
   launcher UI.
3. Keep the engine's `app` module as the main module; keep package
   `ru.playsoftware.j2meloader` for engine Java code if renaming proves
   risky (least-risk option for v0.1.0), changing only `applicationId`,
   `namespace`, and the `FileProvider`/`DocumentProvider` authorities
   (`${applicationId}.provider` / `${applicationId}.documentProvider`).
   Record the decision in the README when it is made.
4. Launcher (this repo's code) calls the engine only through
   `InstallerDialog`/`AppListModel` for install and `Config.startApp()`
   for launch. It must create a default per-game config before first
   launch so auto-run after import is not blocked by `ConfigActivity`.
5. **Do NOT split the engine into a `:engine` library module in v0.1.0.**
   The engine is coupled to shared `R`, `BuildConfig.FULL_EMULATOR`,
   static `ContextHolder`, static `Config` dirs, Room DB, launcher
   resources/layouts, and native `.so` for 4 ABIs. Splitting it is a
   large, high-risk refactor with no benefit for v0.1.0.

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
- `Constants.java` values above are from tag 1.8.2; `Config.java` /
  `MicroActivity` / `MicroLoader` / `AppInstaller` / `Descriptor` logic
  was verified at commit `db0b9d6f` in Phase 1 and must be re-diffed
  against `1.8.2` before code is written against it.
