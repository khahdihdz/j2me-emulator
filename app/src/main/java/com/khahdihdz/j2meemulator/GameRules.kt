package com.khahdihdz.j2meemulator

import java.io.File

/**
 * Phase 7 — pure rules behind Home and the game details screen.
 *
 * This file deliberately imports nothing from Android: every function
 * here takes and returns plain Kotlin/Java values, so it is covered
 * by plain JVM unit tests (`app/src/test`, JUnit 4 — the only test
 * dependency the merged build has, `junit:junit:4.13.2` from the
 * engine's own `app/build.gradle`). The activities in this package
 * are thin adapters around these rules: they extract strings from
 * `Intent`/`Uri`/resources, call a rule, and map the result back to
 * a string resource or a view. The rules below are the exact logic
 * the activities used before Phase 7 (moved here unchanged, except
 * where a comment says otherwise), so the refactor is behaviour-
 * preserving; the tests pin that behaviour.
 *
 * What is NOT here, and why: everything that needs the Android
 * framework or the engine's runtime (`InstallerDialog` /
 * `AppInstaller` install flow, Room, `Config.startApp` launching a
 * MIDlet, `ProfilesManager.loadConfig` Gson parsing, orientation
 * actually rotating the screen) cannot run on the JVM without
 * Robolectric, which is NOT a dependency of this build and is not
 * added in Phase 7 (new dependency, unverified version, flaky Maven
 * downloads — see docs/ENGINE-INTEGRATION.md §14). Those cases are
 * covered by the manual checklist in §14 instead of by fake tests.
 */
object GameUriRules {

    /** File extensions Home accepts as game files. */
    val GAME_EXTENSIONS = setOf("jar", "jad", "kjx")

    /** Uri schemes Home accepts for game files. */
    val GAME_SCHEMES = setOf("file", "content", "http", "https")

    /** Why Home's lenient pre-check rejected a Uri. */
    enum class Error { BAD_SCHEME, BAD_EXTENSION }

    /**
     * Lenient pre-check, only to give the user a clear Vietnamese
     * error for what is obviously not a game file. Returns the error,
     * or null when the Uri may be a game file and the engine should
     * decide (it sniffs magic bytes for `content://` and parses the
     * JAD/manifest itself). `scheme` and `path` are the raw values
     * from `Uri.scheme` / `Uri.path`; both are lowercased here.
     *
     * - Scheme must be one of [GAME_SCHEMES] (anything else, missing
     *   included, is [Error.BAD_SCHEME]).
     * - The extension is the part after the last dot of the path; a
     *   missing or empty extension is accepted (a `content://` Uri
     *   often has none), a present-but-unknown one is
     *   [Error.BAD_EXTENSION].
     */
    fun validate(scheme: String?, path: String?): Error? {
        val normalizedScheme = scheme?.lowercase()
        if (normalizedScheme !in GAME_SCHEMES) return Error.BAD_SCHEME
        // The extension comes from the last path segment only: a dot in
        // the authority or in a directory name (e.g. the host in
        // "content://com.example/1234") is not a file extension, and a
        // Uri without an extension is forwarded — the engine sniffs the
        // magic bytes and decides.
        val fileName = path.orEmpty().substringAfterLast('/')
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension.isNotEmpty() && extension !in GAME_EXTENSIONS) {
            return Error.BAD_EXTENSION
        }
        return null
    }

    /**
     * Which game-file Uri an intent carries, as a string, or null when
     * it carries none. The `action` values are the stable
     * `Intent.ACTION_*` strings (`"android.intent.action.VIEW"` etc.;
     * the constants themselves live in Android, so they are spelled
     * out here). `data` is the VIEW data Uri, `stream` the
     * ACTION_SEND `EXTRA_STREAM` Uri, `streams` the
     * ACTION_SEND_MULTIPLE list (the first item wins, like the engine
     * never did — it has no SEND support at all). A Uri restored from
     * history (`fromHistory`, i.e. `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`)
     * is ignored, like the engine MainActivity does.
     */
    fun pickIntentUri(
        action: String?,
        data: String?,
        stream: String?,
        streams: List<String>?,
        fromHistory: Boolean
    ): String? {
        if (fromHistory) return null
        return when (action) {
            "android.intent.action.VIEW" -> data
            "android.intent.action.SEND" -> stream
            "android.intent.action.SEND_MULTIPLE" -> streams?.firstOrNull()
            else -> null
        }
    }
}

/**
 * Phase 7 — library rules (search and sort) for Home. The match rule
 * and the direction-toggle rule are the engine's own rules, copied
 * from `AppsListAdapter.AppFilter` / the sort dialog at upstream tag
 * 1.8.2 (see docs/ENGINE-INTEGRATION.md §12); Home and the engine
 * Library must keep agreeing, so the rules live here once and the
 * tests pin them.
 */
object LibraryRules {

    /**
     * A game matches a search query when its lowercased title or
     * author contains the lowercased query. An empty query matches
     * everything (an empty string is contained in every string).
     * The query is NOT trimmed: a query with leading/trailing spaces
     * matches nothing, exactly like the engine list.
     */
    fun matches(title: String, author: String, query: String): Boolean {
        val q = query.lowercase()
        return title.lowercase().contains(q) || author.lowercase().contains(q)
    }

    /**
     * Sort-variant toggle. `current` is the stored
     * `PREF_APP_SORT` value, `picked` the variant the user just
     * chose (0 name / 1 date / 2 author). Picking the variant that
     * is already active flips its direction bit (`0x80000000`,
     * i.e. `Int.MIN_VALUE`); picking another variant selects it
     * with the default (ascending) direction.
     */
    fun toggleSort(current: Int, picked: Int): Int =
        if (current == picked) picked or Int.MIN_VALUE else picked
}

/**
 * Phase 7 — formatting and filesystem helpers for the game details
 * screen. Moved here unchanged from `GameDetailsActivity` so they
 * can be unit-tested; the activity now only calls them.
 */
object FormatRules {

    /**
     * Human-readable size. Uses the default locale for `%.2f`
     * (tests pin `Locale.US`, see `FormatRulesTest`).
     */
    fun formatSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            bytes < 1024 -> "$bytes B"
            kb < 1024 -> "%.2f KB".format(kb)
            mb < 1024 -> "%.2f MB".format(mb)
            else -> "%.2f GB".format(mb / 1024.0)
        }
    }

    /**
     * Total size of all files under a directory, or -1 when it is
     * not an existing directory.
     */
    fun dirSize(dir: File): Long {
        if (!dir.isDirectory) return -1
        var total = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) total += file.length()
        }
        return total
    }

    /**
     * Label for a per-game orientation value (0 Default / 1 Auto /
     * 2 Portrait / 3 Landscape), or null when the value is outside
     * the label array (a hand-edited config) — the caller shows the
     * "unknown" string instead of crashing on an array index.
     */
    fun orientationLabel(entries: Array<String>, orientation: Int): String? =
        entries.getOrNull(orientation)

    /**
     * Label for a virtual-keyboard layout variant (0 Custom / 1 Phone
     * / 2 Phone+arrows / 3 Numbers+arrows / 4 Arrows+numbers /
     * 5 Numbers / 6 Arrows), or null when out of range (same rule as
     * [orientationLabel]).
     */
    fun vkLayoutLabel(entries: Array<String>, vkType: Int): String? =
        entries.getOrNull(vkType)
}
