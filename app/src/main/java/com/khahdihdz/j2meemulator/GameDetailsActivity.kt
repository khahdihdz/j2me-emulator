package com.khahdihdz.j2meemulator

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import java.io.File
import ru.playsoftware.j2meloader.R
import ru.playsoftware.j2meloader.applist.AppItem
import ru.playsoftware.j2meloader.applist.AppListModel
import ru.playsoftware.j2meloader.config.Config
import ru.playsoftware.j2meloader.util.AppUtils

/**
 * Phase 3 — Game details (ours). All values arrive as plain extras
 * from [launch]; nothing here reads the engine database to display
 * the game (the size is computed by walking the converted directory).
 *
 * - Icon: the converted game directory contains `icon.png`
 *   (`Config.MIDLET_ICON_FILE`, verified at upstream tag 1.8.2), so the
 *   icon is decoded from `pathExt + Config.MIDLET_ICON_FILE` with plain
 *   `BitmapFactory`. If the file is missing, the ImageView is hidden.
 * - "Chạy" calls `Config.startApp(title, pathExt, false)` (verified:
 *   opens ConfigActivity on first run, MicroActivity afterwards).
 * - "Cài đặt game" calls `Config.startApp(title, pathExt, true)`
 *   (verified: always opens the per-game ConfigActivity).
 * - Size: the converted directory (`pathExt`) is walked on a
 *   background thread and summed; no size field exists on AppItem,
 *   so this is our own computation, not an engine API.
 *
 * Phase 5 — delete. The button asks for confirmation, then deletes
 * exactly what the engine's own delete does (AppsListFragment
 * `alertDelete` at 1.8.2): `AppUtils.deleteApp(item)` removes the
 * converted game directory, the saved-data directory and the settings
 * directory, and `AppRepository.delete(item)` removes the Room row.
 * The original .jar/.jad file is never touched by either call: the
 * installer only ever read it (copied into a cache directory and
 * converted there), so deleting a game cannot delete the file it came
 * from. The Room row is deleted by primary key, so the item is
 * rebuilt here with the id passed in [launch].
 * Rename / reinstall are NOT here: they live in the engine
 * MainActivity's context menu (Phase 5 owns library management, but
 * those two actions already exist there and are not duplicated).
 */
class GameDetailsActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_AUTHOR = "author"
        private const val EXTRA_VERSION = "version"
        private const val EXTRA_PATH = "path"
        private const val EXTRA_PATH_EXT = "pathExt"

        fun launch(context: Context, item: AppItem) {
            val intent = Intent(context, GameDetailsActivity::class.java).apply {
                putExtra(EXTRA_ID, item.id)
                putExtra(EXTRA_TITLE, item.title)
                putExtra(EXTRA_AUTHOR, item.author)
                putExtra(EXTRA_VERSION, item.version)
                putExtra(EXTRA_PATH, item.path)
                putExtra(EXTRA_PATH_EXT, item.pathExt)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(EXTRA_ID, -1)
        val title = intent.getStringExtra(EXTRA_TITLE)
        val path = intent.getStringExtra(EXTRA_PATH)
        val pathExt = intent.getStringExtra(EXTRA_PATH_EXT)
        if (id < 0 || title == null || path == null || pathExt == null) {
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
        findViewById<TextView>(R.id.details_folder).text =
            getString(R.string.details_folder, path)
        showSize(File(pathExt))

        findViewById<Button>(R.id.details_run).setOnClickListener {
            Config.startApp(this, title, pathExt, false)
        }
        findViewById<Button>(R.id.details_settings).setOnClickListener {
            Config.startApp(this, title, pathExt, true)
        }
        findViewById<Button>(R.id.details_delete).setOnClickListener {
            confirmDelete(id, path, title, author, version)
        }
    }

    /** Walk the converted directory on a background thread and show
     * its total size. -1 when the directory is missing. */
    private fun showSize(dir: File) {
        Thread {
            val size = dirSize(dir)
            runOnUiThread {
                val sizeView = findViewById<TextView>(R.id.details_size)
                sizeView.text = if (size >= 0) {
                    getString(R.string.details_size, formatSize(size))
                } else {
                    getString(R.string.details_size_unknown)
                }
            }
        }.start()
    }

    private fun dirSize(dir: File): Long {
        if (!dir.isDirectory) return -1
        var total = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) total += file.length()
        }
        return total
    }

    private fun formatSize(bytes: Long): String {
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
     * Phase 5 — delete with confirmation. Rebuilds the AppItem with
     * its database id (Room @Delete matches on the primary key), then
     * runs the engine's own two delete calls, in the engine's order.
     * The original game file is kept: neither call touches it (see
     * the class doc). Home re-queries its list on resume, so the
     * deleted game disappears there; the delete itself runs on the
     * repository's IO thread, so a very fast return to Home could in
     * theory show the game once more until the next resume re-query.
     */
    private fun confirmDelete(id: Int, path: String, title: String, author: String, version: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.details_delete_title)
            .setMessage(getString(R.string.details_delete_message, title))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                deleteGame(id, path, title, author, version)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun deleteGame(id: Int, path: String, title: String, author: String, version: String) {
        try {
            val item = AppItem(path, title, author, version)
            item.id = id
            AppUtils.deleteApp(item)
            val appListModel = ViewModelProvider(this).get(AppListModel::class.java)
            appListModel.appRepository.delete(item)
            Toast.makeText(this, getString(R.string.details_deleted, title), Toast.LENGTH_LONG).show()
            finish()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.details_delete_failed, title), Toast.LENGTH_LONG).show()
        }
    }
}
