#!/usr/bin/env python3
"""Merge a pristine J2ME Loader checkout into this repo's app module.

Usage (run on CI, after the upstream checkout step):
    python3 tools/merge-engine.py [engine_dir]

engine_dir defaults to vendor/j2me-loader, where the CI workflows check
out nikita36078/J2ME-Loader at tag 1.8.2. The script works on the checked
out tree only: it reads files, copies them into app/ and dexlib/, and
generates the merged app/build.gradle, AndroidManifest.xml and
settings.gradle. It makes no network requests and never touches the
engine checkout itself (except reading it).

Design (all facts verified against upstream tag 1.8.2, see
docs/ENGINE-INTEGRATION.md; nothing here is guessed):

* The engine is NOT a library. It is a whole app (app + dexlib). The
  merge copies its sources into our app module and keeps its module
  structure: app/src/main/java, res, assets, cpp, dexlib/.
* namespace STAYS 'ru.playsoftware.j2meloader'. Engine sources import
  ru.playsoftware.j2meloader.R and ru.playsoftware.j2meloader.BuildConfig
  explicitly (Config.java, MicroActivity.java, InstallerDialog.java,
  EmulatorApplication.java, ...), and unqualified R references in that
  package resolve there too. Changing the namespace would break every
  one of those references. Only applicationId changes, to
  com.khahdihdz.j2meemulator. Providers use ${applicationId}, so their
  authorities follow automatically.
* The engine's MainActivity stays the real library/management screen
  (AppsListFragment: list, search, sort, import, rename, delete,
  reinstall, shortcut). Our own com.khahdihdz.j2meemulator.MainActivity
  is the Home screen (Phase 3: light Theme.J2MEEmulator, app identity,
  read-only game list) that owns MAIN/LAUNCHER and the ACTION_VIEW
  filters, so the merged app has exactly one launcher icon and one
  receiver for .jar/.jad/.kjx files. The merge strips those
  intent-filters from the engine activity's manifest entry. Game-file
  intents are still forwarded to the engine MainActivity by our
  MainActivity; plain launches show our Home. Two more of our
  activities are declared by the merge: GameDetailsActivity and
  AboutActivity (both exported=false, no intent filters).
* Conflict rule: engine wins for same-path files, EXCEPT the value
  files res/values/strings.xml and colors.xml, which are merged by
  resource name (engine wins on a name clash; verified: there is no
  name clash between skeleton and engine for strings or colors).
  One special case: the skeleton's <string name="app_name"> is DROPPED.
  The engine generates app_name with resValue from rootProject.name
  ("J2ME Emulator"), and a second XML definition would be a duplicate
  resource. The other skeleton strings are kept (unused for now).
* app/build.gradle is GENERATED from the engine's file by text
  replacement with assertions: applicationId, versionCode/versionName
  from APP_VERSION_CODE/APP_VERSION_NAME (defaults 1 / "0.1.0", for
  the apksign shared-signing workflow), Kotlin plugin + Java 1.8 /
  jvmTarget 1.8. Everything else (signingConfigs, flavours, native
  build, dependencies) is kept verbatim. Java 1.8 — not the skeleton's
  Java 17 — is deliberate: it matches the effective default the
  pristine engine build used on CI. Do not hand-edit the generated
  file; change this script instead.
* settings.gradle is generated: rootProject.name "J2ME Emulator"
  (this is what the generated app_name resValue becomes in release
  builds; debug builds still get the engine's 'JL-Debug' override
  from applicationVariants), include ':app', ':dexlib'.

Exit code 0 on success, 1 on any failed check. The script is
idempotent: running it twice on the same tree produces the same
result (the manifest and build.gradle are regenerated from the
engine checkout each time; value files are merged as a name union).
"""

import importlib.util
import shutil
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ANDROID_NS = "http://schemas.android.com/apk/res/android"
TOOLS_NS = "http://schemas.android.com/tools"
ET.register_namespace("android", ANDROID_NS)
ET.register_namespace("tools", TOOLS_NS)

APP_ID = "com.khahdihdz.j2meemulator"
ENGINE_NAMESPACE = "ru.playsoftware.j2meloader"
FORWARDER_CLASS = "com.khahdihdz.j2meemulator.MainActivity"
FORWARDER_THEME = "@style/Theme.J2MEEmulator"
# Our own extra activities (Phase 3), declared by the merge with no
# intent filters. Theme.J2MEEmulator comes from the skeleton's
# res/values/themes.xml, which survives the merge because the engine
# has no res/values/themes.xml of its own (verified at tag 1.8.2).
OUR_ACTIVITIES = (
    "com.khahdihdz.j2meemulator.GameDetailsActivity",
    "com.khahdihdz.j2meemulator.AboutActivity",
)

# Engine classes the merged app cannot run without.
REQUIRED_ENGINE_CLASSES = [
    "app/src/main/java/ru/playsoftware/j2meloader/MainActivity.java",
    "app/src/main/java/ru/playsoftware/j2meloader/EmulatorApplication.java",
    "app/src/main/java/ru/playsoftware/j2meloader/config/Config.java",
    "app/src/main/java/ru/playsoftware/j2meloader/config/ConfigActivity.java",
    "app/src/main/java/ru/playsoftware/j2meloader/util/Constants.java",
    "app/src/main/java/javax/microedition/shell/MicroActivity.java",
    "app/src/main/java/javax/microedition/shell/MicroLoader.java",
    "app/src/main/java/ru/woesss/j2me/installer/AppInstaller.java",
    "app/src/main/java/ru/woesss/j2me/installer/InstallerDialog.java",
    # Referenced by the engine manifest's <receiver> (verified at 1.8.2).
    "app/src/main/java/com/nokia/mid/ui/NotificationBroadcastReceiver.java",
]

REQUIRED_PROGUARD_FILES = ["proguard-rules.pro", "proguard-common.pro"]


def android_attr(name):
    return "{%s}%s" % (ANDROID_NS, name)


def fail(msg):
    print(f"FAIL: {msg}")
    return 1


def run_pristine_check(tools_dir, engine):
    """Reuse tools/check-engine-pristine.py; abort the merge if it fails."""
    spec = importlib.util.spec_from_file_location(
        "check_engine_pristine", tools_dir / "check-engine-pristine.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    print("== Step 1: verify engine checkout (check-engine-pristine.py) ==")
    if module.main(["check-engine-pristine.py", str(engine)]) != 0:
        print("FAIL: engine checkout did not pass the pristine check.")
        return False
    return True


def copy_java_sources(engine, app_src):
    """Copy the engine's Java trees into app/src/main/java/."""
    print("== Step 2: copy engine Java sources ==")
    copied = 0
    java_root = engine / "app/src/main/java"
    for top in ("ru", "javax", "org", "com"):
        src = java_root / top
        if not src.is_dir():
            print(f"  --   java/{top}/ absent in engine checkout, skipped")
            continue
        before = sum(1 for _ in (app_src / "java" / top).rglob("*.java")) \
            if (app_src / "java" / top).exists() else 0
        shutil.copytree(src, app_src / "java" / top, dirs_exist_ok=True)
        after = sum(1 for _ in (app_src / "java" / top).rglob("*.java"))
        copied += after - before
        print(f"  OK   java/{top}/ (+{after - before} .java files)")
    # Engine unit/instrumented tests, if the tag has any: copy, don't run here.
    for srcset in ("androidTest", "test"):
        src = engine / "app/src" / srcset
        if src.is_dir():
            shutil.copytree(src, app_src.parent / srcset, dirs_exist_ok=True)
            print(f"  OK   app/src/{srcset}/ copied")
    # Other source sets the merged build.gradle evaluates at
    # configuration time: 'midlet' holds the sample MIDlet manifest
    # that the midlet product flavour reads with
    # getMidletManifestProperties() -> props.getValue('MIDlet-Name')
    # .trim(). Without this copy the merged build fails at
    # configuration time with "Cannot invoke method trim() on null
    # object" (CI run #10, app/build.gradle line 116), even when
    # building fdroidDebug, because productFlavors are evaluated for
    # every flavour. 'debug' is copied for the same reason.
    for srcset in ("midlet", "debug"):
        src = engine / "app/src" / srcset
        if src.is_dir():
            shutil.copytree(src, app_src.parent / srcset, dirs_exist_ok=True)
            print(f"  OK   app/src/{srcset}/ copied")
    return copied


def merge_values_file(engine_file, app_file, drop_names):
    """Union two Android values files by resource name.

    Engine wins on a name clash; names in drop_names are removed from
    the app side (used for app_name, which the engine generates with
    resValue — a second XML definition would be a duplicate resource).
    Returns (added, kept, dropped).
    """
    def parse(path):
        if not path.is_file():
            return None, {}
        tree = ET.parse(path)
        entries = {}
        for child in tree.getroot():
            name = child.get("name")
            if name is not None:
                entries[name] = child
        return tree, entries

    engine_tree, engine_entries = parse(engine_file)
    app_tree, app_entries = parse(app_file)
    if engine_tree is None:
        print(f"  --   {engine_file.name} absent in engine, kept app file")
        return 0, 0, 0
    merged = dict(engine_entries)
    added = kept = dropped = 0
    for name, elem in app_entries.items():
        if name in drop_names:
            dropped += 1
            continue
        if name not in merged:
            merged[name] = elem
            added += 1
        else:
            kept += 1  # engine value wins, app value discarded
    root = engine_tree.getroot()
    for child in list(root):
        root.remove(child)
    for elem in merged.values():
        root.append(elem)
    ET.indent(engine_tree, space="    ")
    engine_tree.write(app_file, encoding="utf-8", xml_declaration=True)
    return added, kept, dropped


def copy_resources(engine, app_main):
    """Copy engine res/assets/cpp, merging values files by name."""
    print("== Step 3: copy engine res / assets / cpp ==")
    strings = app_main / "res/values/strings.xml"
    colors = app_main / "res/values/colors.xml"
    # Snapshot the skeleton values BEFORE the engine copy overwrites them.
    strings_snapshot = strings.read_bytes() if strings.is_file() else None
    colors_snapshot = colors.read_bytes() if colors.is_file() else None
    tmp_strings = Path(str(strings) + ".skeleton")
    tmp_colors = Path(str(colors) + ".skeleton")
    if strings_snapshot is not None:
        tmp_strings.write_bytes(strings_snapshot)
    if colors_snapshot is not None:
        tmp_colors.write_bytes(colors_snapshot)
    try:
        engine_res = engine / "app/src/main/res"
        shutil.copytree(engine_res, app_main / "res", dirs_exist_ok=True)
        print(f"  OK   res/ copied (engine wins on same-path files)")
        # Re-merge the skeleton values on top of the engine's files.
        for tmp, target, drop in (
                (tmp_strings, strings, {"app_name"}),
                (tmp_colors, colors, set())):
            if not tmp.exists():
                continue
            engine_values = (engine / "app/src/main/res/values"
                             / target.name)
            if not engine_values.is_file():
                # Engine has no such values file; the copytree result
                # (skeleton file) is already in place. Do not touch it.
                print(f"  --   {target.name} absent in engine values, "
                      f"kept skeleton file")
                continue
            added, kept, dropped = merge_values_file(
                engine_values, tmp, drop)
            # merge_values_file wrote the merged result into tmp;
            # move it into place (engine file is the merge base).
            target.write_text(tmp.read_text(encoding="utf-8"),
                              encoding="utf-8")
            print(f"  OK   res/values/{target.name}: "
                  f"+{added} skeleton entries, {kept} engine-wins, "
                  f"{dropped} dropped ({sorted(drop) if drop else 'none'})")
        for sub in ("assets", "cpp"):
            src = engine / "app/src/main" / sub
            if src.is_dir():
                shutil.copytree(src, app_main / sub, dirs_exist_ok=True)
                print(f"  OK   {sub}/ copied")
            else:
                print(f"  --   {sub}/ absent in engine, skipped")
        # Any other top-level files/dirs under app/src/main (not
        # AndroidManifest.xml, which is generated in step 6).
        for entry in sorted((engine / "app/src/main").iterdir()):
            if entry.name in ("java", "res", "assets", "cpp",
                              "AndroidManifest.xml"):
                continue
            dest = app_main / entry.name
            if entry.is_dir():
                shutil.copytree(entry, dest, dirs_exist_ok=True)
            else:
                shutil.copy2(entry, dest)
            print(f"  OK   {entry.name} copied (extra engine content)")
    finally:
        tmp_strings.unlink(missing_ok=True)
        tmp_colors.unlink(missing_ok=True)


def copy_proguard_and_dexlib(engine, root):
    print("== Step 4: copy proguard files and dexlib module ==")
    for name in REQUIRED_PROGUARD_FILES:
        src = engine / "app" / name
        if not src.is_file():
            return fail(f"engine app/{name} missing "
                        f"(referenced by every full-emulator flavour)")
        shutil.copy2(src, root / "app" / name)
        print(f"  OK   app/{name}")
    for name in ("proguard-midlet.pro", "multidex-config.pro"):
        src = engine / "app" / name
        if src.is_file():
            shutil.copy2(src, root / "app" / name)
            print(f"  OK   app/{name}")
    shutil.copytree(engine / "dexlib", root / "dexlib", dirs_exist_ok=True)
    if not (root / "dexlib/build.gradle").is_file():
        return fail("dexlib/build.gradle missing after copy")
    print("  OK   dexlib/ copied")
    return 0


def merged_build_gradle(engine_text):
    """Apply the documented edits to the engine's app/build.gradle."""
    edits = []

    def replace_once(old, new, what):
        nonlocal engine_text
        if engine_text.count(old) != 1:
            raise ValueError(
                f"expected exactly 1 occurrence of {what}, "
                f"found {engine_text.count(old)}")
        engine_text = engine_text.replace(old, new, 1)
        edits.append(what)

    replace_once(
        "plugins {\n    id 'com.android.application'\n}",
        "plugins {\n    id 'com.android.application'\n"
        "    id 'org.jetbrains.kotlin.android'\n}",
        "add kotlin-android plugin")
    # namespace is deliberately KEPT (see module docstring).
    if "namespace 'ru.playsoftware.j2meloader'" not in engine_text:
        raise ValueError("namespace line not found")
    edits.append("keep namespace 'ru.playsoftware.j2meloader'")
    replace_once(
        'applicationId "ru.playsoftware.j2meloader"',
        f'applicationId "{APP_ID}"',
        "applicationId -> com.khahdihdz.j2meemulator")
    replace_once(
        "        versionCode 101\n",
        "        // Version comes from the apksign shared-signing workflow\n"
        "        // via env vars (APP_VERSION_CODE / APP_VERSION_NAME);\n"
        "        // defaults are v0.1.0 / code 1.\n"
        "        versionCode = System.getenv(\"APP_VERSION_CODE\")"
        "?.toInteger() ?: 1\n",
        "versionCode from env (was 101)")
    replace_once(
        '        versionName "1.8.2"\n',
        "        versionName = System.getenv(\"APP_VERSION_NAME\")"
        " ?: \"0.1.0\"\n",
        'versionName from env (was "1.8.2")')
    replace_once(
        "        minSdk MIN_SDK\n",
        "        // minSdk 19 (was MIN_SDK = 14): the engine calls\n"
        "        // BluetoothDevice.fetchUuidsWithSdp (API 15) and\n"
        "        // android.util.ArrayMap (API 19); lint NewApi flagged\n"
        "        // all four call sites at minSdk 14 (CI run #12, 27\n"
        "        // lint errors). Upstream's README says Android 5.0+\n"
        "        // (API 21), so 19 stays below what upstream claims\n"
        "        // to support.\n"
        "        minSdk 19\n",
        "minSdk 14 -> 19 (NewApi fix)")
    replace_once(
        "        disable 'MissingTranslation', 'ExtraTranslation'\n",
        "        // 'MissingPermission' added: lint flags two legacy\n"
        "        // J2ME Bluetooth/location calls in engine code\n"
        "        // (LocalDevice.getAddress, LocationProviderImpl.\n"
        "        // removeUpdates). Upstream never ran lint on tag\n"
        "        // 1.8.2; this is a lint-only suppression — runtime\n"
        "        // behaviour is unchanged from the pristine build.\n"
        "        disable 'MissingTranslation', 'ExtraTranslation',\n"
        "                'MissingPermission'\n",
        "lint: disable MissingPermission")
    replace_once(
        "    signingConfigs {",
        "    compileOptions {\n"
        "        // Java 1.8 matches the effective default the pristine\n"
        "        // engine build used on CI (the engine has no\n"
        "        // compileOptions block of its own). The skeleton used\n"
        "        // Java 17; do not raise this without rebuilding the\n"
        "        // engine sources against it first.\n"
        "        sourceCompatibility JavaVersion.VERSION_1_8\n"
        "        targetCompatibility JavaVersion.VERSION_1_8\n"
        "    }\n"
        "    kotlinOptions {\n"
        "        jvmTarget = '1.8'\n"
        "    }\n"
        "\n"
        "    signingConfigs {",
        "add compileOptions/kotlinOptions (Java 1.8)")
    header = (
        "// GENERATED by tools/merge-engine.py from upstream J2ME Loader\n"
        "// tag 1.8.2 app/build.gradle. Do not edit by hand — change the\n"
        "// merge script instead. Edits applied, in order:\n"
        + "".join("//   - %s\n" % e for e in edits)
    )
    return header + engine_text


def merged_manifest(engine_manifest):
    """Transform the engine manifest: our forwarder owns the launcher
    filters; the engine MainActivity keeps its declaration only.

    Phase 4: the forwarder also inherits the engine MainActivity's
    launchMode (singleTask) and configChanges, so a second game-file
    intent while Home is already running arrives via onNewIntent
    instead of stacking a new Home instance; and it gains one extra
    ACTION_SEND filter (the engine has none — it never reads
    EXTRA_STREAM), so other apps can share a .jar/.jad with us.
    """
    tree = ET.parse(engine_manifest)
    root = tree.getroot()
    app = root.find("application")
    if app is None:
        raise ValueError("no <application> in engine manifest")
    activities = [c for c in app if c.tag == "activity"]
    engine_main = None
    for act in activities:
        if act.get(android_attr("name")) in (".MainActivity",
                                             "ru.playsoftware.j2meloader.MainActivity"):
            engine_main = act
            break
    if engine_main is None:
        raise ValueError("engine .MainActivity not found in manifest")
    filters = [c for c in engine_main if c.tag == "intent-filter"]
    if len(filters) != 4:
        raise ValueError(
            f"expected 4 intent-filters on engine MainActivity, "
            f"found {len(filters)}")
    for f in filters:
        engine_main.remove(f)
    forwarder = ET.Element("activity")
    forwarder.set(android_attr("name"), FORWARDER_CLASS)
    forwarder.set(android_attr("exported"), "true")
    forwarder.set(android_attr("theme"), FORWARDER_THEME)
    # Inherit the entry-point behaviour from the engine MainActivity
    # (verified at tag 1.8.2: singleTask + the orientation/screenSize
    # configChanges). Without singleTask, onNewIntent in Home would
    # never fire and every file intent would stack a new Home.
    launch_mode = engine_main.get(android_attr("launchMode"))
    if launch_mode != "singleTask":
        raise ValueError("engine MainActivity is not singleTask "
                         f"(found {launch_mode!r}); Home relies on it")
    forwarder.set(android_attr("launchMode"), launch_mode)
    config_changes = engine_main.get(android_attr("configChanges"))
    if config_changes is None:
        raise ValueError("engine MainActivity has no configChanges")
    forwarder.set(android_attr("configChanges"), config_changes)
    for f in filters:
        forwarder.append(f)
    # Phase 4: ACTION_SEND filter. The engine has no SEND support at
    # all (no filter, never reads EXTRA_STREAM); Home extracts
    # EXTRA_STREAM itself and forwards it as a VIEW data Uri to the
    # engine MainActivity, so the filter lives here, on Home.
    send_filter = ET.Element("intent-filter")
    send_action = ET.Element("action")
    send_action.set(android_attr("name"), "android.intent.action.SEND")
    send_filter.append(send_action)
    send_category = ET.Element("category")
    send_category.set(android_attr("name"),
                      "android.intent.category.DEFAULT")
    send_filter.append(send_category)
    for mime in ("application/java-archive",
                 "application/x-java-archive",
                 "application/java",
                 "text/vnd.sun.j2me.app-descriptor"):
        data = ET.Element("data")
        data.set(android_attr("mimeType"), mime)
        send_filter.append(data)
    forwarder.append(send_filter)
    idx = list(app).index(engine_main)
    app.insert(idx + 1, forwarder)
    # Our own Phase 3 activities (no intent filters, not exported).
    pos = idx + 2
    for cls in OUR_ACTIVITIES:
        act = ET.Element("activity")
        act.set(android_attr("name"), cls)
        act.set(android_attr("exported"), "false")
        act.set(android_attr("theme"), FORWARDER_THEME)
        app.insert(pos, act)
        pos += 1
    ET.indent(tree, space="    ")
    return tree


def write_settings(root):
    text = (
        "// GENERATED by tools/merge-engine.py. rootProject.name is what\n"
        "// the generated app_name resValue becomes in release builds.\n"
        "pluginManagement {\n"
        "    repositories {\n"
        "        mavenLocal()\n"
        "        gradlePluginPortal()\n"
        "        google()\n"
        "        mavenCentral()\n"
        "    }\n"
        "}\n"
        "dependencyResolutionManagement {\n"
        "    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)\n"
        "    repositories {\n"
        "        mavenLocal()\n"
        "        google()\n"
        "        mavenCentral()\n"
        "        maven { url \"https://jitpack.io\" }\n"
        "    }\n"
        "}\n"
        "rootProject.name = \"J2ME Emulator\"\n"
        "include ':app', ':dexlib'\n"
    )
    (root / "settings.gradle").write_text(text, encoding="utf-8")


def apply_lint_fixes(root):
    """Fix the lint errors that are real, minimal bugs in engine code.

    Verified against the full lint report of CI run #12 (27 errors,
    168 warnings; only the 27 errors block the build):
    * StringFormatMatches (19): MainActivity.alertCreateDir() passes
      TWO args to alert_msg_workdir_not_exists, whose text has ONE
      %1$s placeholder (the second arg, lblChange, is the dialog's
      neutral-button label, used separately at the next line).
      Passing the extra arg is harmless at runtime (String.format
      ignores extras) but lint flags the call site and every locale's
      definition of the string. Fix: pass only emulatorDir, AND fix
      the one locale that disagrees — values-ru/strings.xml gives
      the same string TWO placeholders (%1$s path, %2$s button),
      so after the call-site fix lint flags ru instead (CI run #15:
      2 errors left). Fix: drop the ", нажав кнопку %2$s," clause
      from the Russian text, keeping its meaning (the button is
      still shown as the dialog's neutral button).
    * UseAppTint (2): listitem_checkable.xml and listitem_dir.xml use
      android:tint on an ImageView that already uses app:srcCompat;
      both layouts already declare xmlns:app. Fix: app:tint.
    * StringFormatMatches, last one (CI run #17: 1 error left):
      values-pt/strings.xml err_missing_app reads "Verifique a% de
      disponibilidade" — the %s lost its 's', so lint parses "% d"
      (percent, space, 'd' of "de") and it conflicts with the base
      string's %s. The call site (ConfigActivity) passes one String
      (storageName), so the fix is "a% de" -> "a%s de", matching base.
    The other two error groups are handled in merged_build_gradle:
    NewApi (4) by minSdk 19, MissingPermission (2) by a lint-only
    disable. Each replacement asserts exactly one occurrence; a
    missing match fails the merge instead of silently shipping the
    un-fixed file.
    """
    print("== Step 8: apply lint fixes to merged engine code ==")
    main_activity = (root / "app/src/main/java/ru/playsoftware"
                     / "j2meloader/MainActivity.java")
    text = main_activity.read_text(encoding="utf-8")
    old = ("getString(R.string.alert_msg_workdir_not_exists, "
           "emulatorDir, lblChange)")
    new = "getString(R.string.alert_msg_workdir_not_exists, emulatorDir)"
    if text.count(old) == 0 and text.count(new) == 1:
        print("  --   MainActivity format call already fixed, skipped")
    elif text.count(old) != 1:
        return fail(f"MainActivity format-call fix: expected 1 "
                    f"occurrence, found {text.count(old)}")
    else:
        main_activity.write_text(text.replace(old, new), encoding="utf-8")
        print("  OK   MainActivity: format call now passes 1 arg")
    # Russian translation of the same string has 2 placeholders while
    # the base string and the other 17 locales have 1; see docstring.
    ru_strings = root / "app/src/main/res/values-ru/strings.xml"
    text = ru_strings.read_text(encoding="utf-8")
    old_ru = ", нажав кнопку %2$s,"
    new_ru = ""
    if text.count(old_ru) == 0 and \
            "Вы можете выбрать другую сейчас и в любое время" in text:
        print("  --   values-ru alert string already 1-arg, skipped")
    elif text.count(old_ru) != 1:
        return fail(f"values-ru alert string fix: expected 1 "
                    f"occurrence, found {text.count(old_ru)}")
    else:
        ru_strings.write_text(text.replace(old_ru, new_ru),
                              encoding="utf-8")
        print("  OK   values-ru: alert string now takes 1 arg")
    # Portuguese err_missing_app: "a% de" is a broken %s (see docstring).
    pt_strings = root / "app/src/main/res/values-pt/strings.xml"
    text = pt_strings.read_text(encoding="utf-8")
    old_pt = "Verifique a% de disponibilidade"
    new_pt = "Verifique a%s de disponibilidade"
    if text.count(old_pt) == 0 and text.count(new_pt) == 1:
        print("  --   values-pt err_missing_app already fixed, skipped")
    elif text.count(old_pt) != 1:
        return fail(f"values-pt err_missing_app fix: expected 1 "
                    f"occurrence, found {text.count(old_pt)}")
    else:
        pt_strings.write_text(text.replace(old_pt, new_pt),
                              encoding="utf-8")
        print("  OK   values-pt: err_missing_app %s restored")
    for name in ("listitem_checkable.xml", "listitem_dir.xml"):
        path = root / "app/src/main/res/layout" / name
        text = path.read_text(encoding="utf-8")
        if 'xmlns:app="http://schemas.android.com/apk/res-auto"' \
                not in text:
            return fail(f"{name}: xmlns:app missing, cannot use app:tint")
        old = 'android:tint="?attr/nnf_dir_icon_color"'
        new = 'app:tint="?attr/nnf_dir_icon_color"'
        if text.count(old) == 0 and text.count(new) == 1:
            print(f"  --   {name} already uses app:tint, skipped")
            continue
        if text.count(old) != 1:
            return fail(f"{name}: expected 1 android:tint, "
                        f"found {text.count(old)}")
        path.write_text(text.replace(old, new), encoding="utf-8")
        print(f"  OK   {name}: android:tint -> app:tint")
    return 0


def apply_phase4_fixes(root):
    """Phase 4: auto-run after a successful install.

    Verified upstream behaviour at tag 1.8.2 (InstallerDialog
    onProgress, STATUS_SUCCESS): the dialog shows "Application
    successfully installed!" and turns its Install button into a
    START button — the game only runs when the user taps it. The
    launcher prompt requires the game to run automatically after a
    successful import, so the merged dialog starts the game at once
    instead (Config.startApp is the verified entry point; on a
    game's first run it opens ConfigActivity, otherwise
    MicroActivity directly). This is a deliberate behaviour change
    vs upstream, applied only to the merged copy, asserted here.
    Idempotent: if the patch marker is already present, skip.
    """
    print("== Step 9: apply Phase 4 import fixes ==")
    path = (root / "app/src/main/java/ru/woesss/j2me/installer"
            / "InstallerDialog.java")
    text = path.read_text(encoding="utf-8")
    marker = "// J2ME Emulator Phase 4: auto-run after install"
    if marker in text:
        print("  --   InstallerDialog auto-run already applied, skipped")
        return 0
    old = (
        "\t\t\tbtnOk.setText(R.string.START_CMD);\n"
        "\t\t\tbtnOk.setOnClickListener(v -> {\n"
        "\t\t\t\tConfig.startApp(v.getContext(), app.getTitle(), "
        "app.getPathExt(), false);\n"
        "\t\t\t\tdismiss();\n"
        "\t\t\t});\n"
        "\t\t\tbtnClose.setText(R.string.close);\n"
        "\t\t\tshowButtons();\n"
        "\t\t\treturn;\n"
    )
    if text.count(old) != 1:
        return fail("InstallerDialog auto-run patch: expected 1 "
                    f"occurrence of the START-button block, found "
                    f"{text.count(old)}")
    new = (
        "\t\t\t" + marker + " (launcher prompt requires the game\n"
        "\t\t\t// to run automatically after a successful import;\n"
        "\t\t\t// upstream showed a START button here instead).\n"
        "\t\t\tConfig.startApp(requireActivity(), app.getTitle(), "
        "app.getPathExt(), false);\n"
        "\t\t\tdismiss();\n"
        "\t\t\treturn;\n"
    )
    path.write_text(text.replace(old, new), encoding="utf-8")
    print("  OK   InstallerDialog: auto-run on STATUS_SUCCESS")
    return 0


def final_checks(root):
    print("== Step 7: final checks ==")
    app_main = root / "app/src/main"
    java_count = sum(1 for _ in (app_main / "java").rglob("*.java"))
    print(f"  Java files in merged app: {java_count}")
    for rel in REQUIRED_ENGINE_CLASSES:
        if not (root / rel).is_file():
            return fail(f"required engine class missing after merge: {rel}")
    print("  OK   all required engine classes present")
    for top in ("org", "com"):
        # The real 1.8.2 tree has org/microemu and com/nokia sources;
        # warn (don't fail) if a copy produced neither, so a partial
        # checkout is visible instead of silently building without them.
        if not (app_main / "java" / top).is_dir():
            print(f"  WARN java/{top}/ absent after merge "
                  f"(real 1.8.2 tree has it; check the checkout)")
    gradle = (root / "app/build.gradle").read_text(encoding="utf-8")
    for needle, what in (
            (f'applicationId "{APP_ID}"', "applicationId"),
            ("namespace 'ru.playsoftware.j2meloader'", "namespace"),
            ("APP_VERSION_CODE", "versionCode from env"),
            ("APP_VERSION_NAME", "versionName from env"),
            ("id 'org.jetbrains.kotlin.android'", "kotlin plugin"),
            ("minSdk 19", "minSdk 19 (NewApi fix)"),
            ("'MissingPermission'", "lint MissingPermission disable")):
        if needle not in gradle:
            return fail(f"merged app/build.gradle missing {what}")
    print("  OK   merged app/build.gradle (applicationId, namespace, "
          "env versions, kotlin plugin, minSdk 19, lint disable)")
    main_activity = (root / "app/src/main/java/ru/playsoftware"
                     / "j2meloader/MainActivity.java").read_text(
                         encoding="utf-8")
    if "alert_msg_workdir_not_exists, emulatorDir, lblChange" \
            in main_activity:
        return fail("MainActivity still passes 2 args to "
                    "alert_msg_workdir_not_exists (StringFormatMatches)")
    for name in ("listitem_checkable.xml", "listitem_dir.xml"):
        if "android:tint" in (root / "app/src/main/res/layout"
                              / name).read_text(encoding="utf-8"):
            return fail(f"{name} still uses android:tint (UseAppTint)")
    if ", нажав кнопку %2$s," in (root / "app/src/main/res/values-ru"
                                 / "strings.xml").read_text(
                                     encoding="utf-8"):
        return fail("values-ru alert string still takes 2 args "
                    "(StringFormatMatches)")
    print("  OK   lint fixes applied (format call, app:tint, ru string)")
    if "Verifique a% de disponibilidade" in \
            (root / "app/src/main/res/values-pt/strings.xml").read_text(
                encoding="utf-8"):
        return fail("values-pt err_missing_app still has broken %s "
                    "(StringFormatMatches)")
    if ":dexlib" not in (root / "settings.gradle").read_text(
            encoding="utf-8"):
        return fail("settings.gradle does not include ':dexlib'")
    print("  OK   settings.gradle includes ':dexlib'")
    midlet_manifest = (root / "app/src/midlet/resources/MIDLET-META-INF"
                       / "MANIFEST.MF")
    if not midlet_manifest.is_file():
        return fail("app/src/midlet manifest missing after merge: "
                    "the midlet flavour reads MIDlet-Name from it at "
                    "Gradle configuration time and fails with "
                    "'Cannot invoke method trim() on null object' "
                    "without it (CI run #10)")
    print("  OK   app/src/midlet sample manifest present")
    tree = ET.parse(app_main / "AndroidManifest.xml")
    app = tree.getroot().find("application")
    launcher = []
    view_filters = []
    send_filters = []
    micro = None
    for act in app.findall("activity"):
        for f in act.findall("intent-filter"):
            actions = [a.get(android_attr("name"))
                       for a in f.findall("action")]
            if "android.intent.action.MAIN" in actions:
                launcher.append(act.get(android_attr("name")))
            if "android.intent.action.VIEW" in actions:
                view_filters.append(act.get(android_attr("name")))
            if "android.intent.action.SEND" in actions:
                send_filters.append(act.get(android_attr("name")))
        if act.get(android_attr("name")) == \
                "javax.microedition.shell.MicroActivity":
            micro = act
    if launcher != [FORWARDER_CLASS]:
        return fail(f"MAIN/LAUNCHER must belong only to the forwarder, "
                    f"found: {launcher}")
    if sorted(set(view_filters)) != [FORWARDER_CLASS]:
        return fail(f"VIEW filters must belong only to the forwarder, "
                    f"found: {sorted(set(view_filters))}")
    if send_filters != [FORWARDER_CLASS]:
        return fail("ACTION_SEND filter must belong only to the "
                    f"forwarder, found: {send_filters}")
    if micro is None or micro.get(android_attr("exported")) != "false" \
            or micro.get(android_attr("process")) != ":midlet":
        return fail("MicroActivity must stay exported=false, "
                    "process=:midlet")
    by_name = {act.get(android_attr("name")): act
               for act in app.findall("activity")}
    home = by_name.get(FORWARDER_CLASS)
    if home is None or home.get(android_attr("theme")) != FORWARDER_THEME:
        return fail("Home (forwarder) activity must use "
                    f"theme {FORWARDER_THEME}")
    for cls in OUR_ACTIVITIES:
        act = by_name.get(cls)
        if act is None or act.get(android_attr("exported")) != "false" \
                or act.get(android_attr("theme")) != FORWARDER_THEME:
            return fail(f"{cls} must be declared, exported=false, "
                        f"theme {FORWARDER_THEME}")
    if home.get(android_attr("launchMode")) != "singleTask":
        return fail("Home must be singleTask so a second file intent "
                    "arrives via onNewIntent")
    if home.get(android_attr("configChanges")) is None:
        return fail("Home must declare configChanges (inherited from "
                    "the engine MainActivity)")
    installer_dialog = (root / "app/src/main/java/ru/woesss/j2me"
                        / "installer/InstallerDialog.java").read_text(
                            encoding="utf-8")
    if "// J2ME Emulator Phase 4: auto-run after install" \
            not in installer_dialog:
        return fail("InstallerDialog auto-run patch (Phase 4) missing")
    print("  OK   merged manifest: one LAUNCHER (Home, singleTask), "
          "VIEW + SEND filters only on Home, MicroActivity "
          "exported=false in :midlet process, our Details/About "
          "activities declared; InstallerDialog auto-runs on success")
    print(f"PASS: engine merged. {java_count} Java files in app.")
    return 0


def main(argv):
    root = Path(__file__).resolve().parent.parent
    engine = Path(argv[1] if len(argv) > 1 else "vendor/j2me-loader")
    if not engine.is_absolute():
        engine = (Path.cwd() / engine).resolve()
    print(f"Repo root: {root}")
    print(f"Engine dir: {engine}")
    tools_dir = root / "tools"
    if not run_pristine_check(tools_dir, engine):
        return 1
    app_main = root / "app/src/main"
    copied = copy_java_sources(engine, app_main)
    print(f"  Java files copied from engine: {copied}")
    copy_resources(engine, app_main)
    rc = copy_proguard_and_dexlib(engine, root)
    if rc:
        return rc
    print("== Step 5: generate merged app/build.gradle ==")
    try:
        merged = merged_build_gradle(
            (engine / "app/build.gradle").read_text(encoding="utf-8"))
    except ValueError as exc:
        return fail(str(exc))
    (root / "app/build.gradle").write_text(merged, encoding="utf-8")
    print("  OK   app/build.gradle generated from engine 1.8.2 file")
    print("== Step 6: generate merged AndroidManifest.xml + "
          "settings.gradle ==")
    try:
        tree = merged_manifest(engine / "app/src/main/AndroidManifest.xml")
    except ValueError as exc:
        return fail(str(exc))
    tree.write(app_main / "AndroidManifest.xml", encoding="utf-8",
               xml_declaration=True)
    print("  OK   AndroidManifest.xml generated (forwarder owns "
          "launcher filters)")
    write_settings(root)
    print("  OK   settings.gradle generated (include ':app', ':dexlib')")
    rc = apply_lint_fixes(root)
    if rc:
        return rc
    rc = apply_phase4_fixes(root)
    if rc:
        return rc
    return final_checks(root)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
