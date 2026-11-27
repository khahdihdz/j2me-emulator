package com.khahdihdz.j2meemulator

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import ru.playsoftware.j2meloader.MainActivity as EngineMainActivity

/**
 * Launcher entry point (v0.1.0, after the engine merge).
 *
 * This activity is only a forwarder. It never draws anything (theme
 * `Theme.NoDisplay` in the merged manifest) and it owns the MAIN/LAUNCHER
 * and ACTION_VIEW intent filters for .jar/.jad/.kjx files, so the app has
 * exactly one launcher icon and one receiver for game files.
 *
 * Everything real happens in the engine activity
 * `ru.playsoftware.j2meloader.MainActivity` (verified at upstream tag 1.8.2):
 * - Cold start: its onCreate hosts `AppsListFragment.newInstance(uri)`,
 *   and `AppsListFragment.onDbUpdated` shows
 *   `InstallerDialog.newInstance(uri)` when a file Uri was received.
 * - Warm start: its onNewIntent shows `InstallerDialog.newInstance(uri)`.
 * - After a successful install, `InstallerDialog` calls
 *   `Config.startApp(context, app.title, app.pathExt, false)`, which opens
 *   `ConfigActivity` on first run or `MicroActivity` directly afterwards.
 *
 * Why a forwarder instead of launching the engine activity directly?
 * Keeping the engine's MainActivity as the launcher would also work, but
 * then every launcher change in Phase 3 would mean editing engine files.
 * The forwarder keeps all of our own entry-point code in one small file
 * and leaves the engine sources untouched. It deliberately does NOT call
 * `InstallerDialog` or `Config.startApp` itself: those are Fragment /
 * post-install APIs that belong to the engine activity's lifecycle.
 *
 * History guard: like the engine's MainActivity, a Uri restored from
 * history (FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) is not forwarded again,
 * so reopening the app from Recents cannot re-trigger an install.
 * Recreation (savedInstanceState != null) just finishes: the engine
 * activity is already in this task.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finish()
            return
        }
        forward(intent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        forward(intent)
        finish()
    }

    private fun forward(source: Intent?) {
        val target = Intent(this, EngineMainActivity::class.java)
        if (source != null &&
            (source.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0
        ) {
            source.data?.let { target.data = it }
            target.action = source.action
        }
        startActivity(target)
    }
}
