package com.khahdihdz.j2meemulator

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import ru.playsoftware.j2meloader.R
import ru.playsoftware.j2meloader.applist.AppItem
import ru.playsoftware.j2meloader.config.Config

/**
 * Phase 3 — Game details (ours). All values arrive as plain string
 * extras from [launch]; nothing here reads the engine database.
 *
 * - Icon: the converted game directory contains `icon.png`
 *   (`Config.MIDLET_ICON_FILE`, verified at upstream tag 1.8.2), so the
 *   icon is decoded from `pathExt + Config.MIDLET_ICON_FILE` with plain
 *   `BitmapFactory`. If the file is missing, the ImageView is hidden.
 * - "Chạy" calls `Config.startApp(title, pathExt, false)` (verified:
 *   opens ConfigActivity on first run, MicroActivity afterwards).
 * - "Cài đặt game" calls `Config.startApp(title, pathExt, true)`
 *   (verified: always opens the per-game ConfigActivity).
 * - Rename / delete / reinstall are NOT here: they live in the engine
 *   MainActivity's context menu (Phase 5 owns library management).
 */
class GameDetailsActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_AUTHOR = "author"
        private const val EXTRA_VERSION = "version"
        private const val EXTRA_PATH_EXT = "pathExt"

        fun launch(context: Context, item: AppItem) {
            val intent = Intent(context, GameDetailsActivity::class.java).apply {
                putExtra(EXTRA_TITLE, item.title)
                putExtra(EXTRA_AUTHOR, item.author)
                putExtra(EXTRA_VERSION, item.version)
                putExtra(EXTRA_PATH_EXT, item.pathExt)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(EXTRA_TITLE)
        val pathExt = intent.getStringExtra(EXTRA_PATH_EXT)
        if (title == null || pathExt == null) {
            // Launched without the extras launch() always sets: nothing
            // meaningful to show.
            finish()
            return
        }
        val author = intent.getStringExtra(EXTRA_AUTHOR) ?: ""
        val version = intent.getStringExtra(EXTRA_VERSION) ?: ""
        setContentView(R.layout.activity_game_details)

        val iconView = findViewById<ImageView>(R.id.details_icon)
        val iconFile = File(pathExt + Config.MIDLET_ICON_FILE)
        val bitmap = if (iconFile.isFile) {
            BitmapFactory.decodeFile(iconFile.absolutePath)
        } else {
            null
        }
        if (bitmap != null) {
            iconView.setImageBitmap(bitmap)
        } else {
            iconView.visibility = View.GONE
        }

        findViewById<TextView>(R.id.details_title).text = title
        findViewById<TextView>(R.id.details_author).text =
            getString(R.string.details_author, author)
        findViewById<TextView>(R.id.details_version).text =
            getString(R.string.details_version, version)
        findViewById<TextView>(R.id.details_path).text =
            getString(R.string.details_path, pathExt)

        findViewById<Button>(R.id.details_run).setOnClickListener {
            Config.startApp(this, title, pathExt, false)
        }
        findViewById<Button>(R.id.details_settings).setOnClickListener {
            Config.startApp(this, title, pathExt, true)
        }
    }
}
