package com.khahdihdz.j2meemulator

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import ru.playsoftware.j2meloader.BuildConfig
import ru.playsoftware.j2meloader.MainActivity as EngineMainActivity
import ru.playsoftware.j2meloader.R

/**
 * Phase 3 — About (ours). Shows our app name and version (from the
 * engine's BuildConfig, whose versionName comes from the
 * APP_VERSION_NAME env var, default "0.1.0"), the engine credit
 * (J2ME Loader 1.8.2, Apache License 2.0) and the repository links,
 * plus a short how-to in Vietnamese.
 */
class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<TextView>(R.id.about_version).text =
            getString(R.string.about_version, BuildConfig.VERSION_NAME)

        findViewById<Button>(R.id.about_open_library).setOnClickListener {
            startActivity(Intent(this, EngineMainActivity::class.java))
        }
    }
}
