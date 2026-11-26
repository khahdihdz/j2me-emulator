package com.khahdihdz.j2meemulator

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Skeleton launcher (v0.1.0).
 *
 * Honest status: the J2ME Loader engine is NOT integrated yet, so this
 * activity cannot import or run any game. It only receives an ACTION_VIEW
 * intent for a .jar/.jad file (same guard as upstream MainActivity:
 * only when savedInstanceState == null and the intent was not restored
 * from history) and shows what was received.
 *
 * No engine API is called here — Config.startApp() / MicroActivity /
 * AppInstaller do not exist in this module yet (see docs/ENGINE-INTEGRATION.md).
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val statusView: TextView = findViewById(R.id.status_text)
        val receivedView: TextView = findViewById(R.id.received_text)

        if (savedInstanceState == null &&
            intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0
        ) {
            val uri = intent.data
            if (uri != null) {
                receivedView.text = getString(R.string.received_file, uri.toString())
            } else {
                receivedView.text = getString(R.string.no_file_received)
            }
        } else {
            receivedView.text = getString(R.string.no_file_received)
        }
        statusView.text = getString(R.string.engine_not_integrated)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Phase 4 will route a new intent to the installer dialog.
        // Skeleton behaviour: ignore it and keep showing the status screen.
        setIntent(intent)
    }
}
