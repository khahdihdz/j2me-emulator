package com.khahdihdz.j2meemulator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.io.File
import ru.playsoftware.j2meloader.MainActivity as EngineMainActivity
import ru.playsoftware.j2meloader.R
import ru.playsoftware.j2meloader.applist.AppItem
import ru.playsoftware.j2meloader.applist.AppListModel
import ru.playsoftware.j2meloader.appsdb.AppRepository
import ru.playsoftware.j2meloader.config.Config
import ru.playsoftware.j2meloader.settings.SettingsActivity
import ru.playsoftware.j2meloader.util.AppUtils

/**
 * Phase 3 — Home screen. This activity is the app's launcher entry
 * (MAIN/LAUNCHER + the ACTION_VIEW filters + one ACTION_SEND filter,
 * owned by this activity in the merged manifest; its theme there is
 * Theme.J2MEEmulator, light blue/white; launchMode singleTask,
 * inherited from the engine MainActivity). It is also the Splash: the
 * first thing the user sees is the app name, version and work
 * directory on a white card, then the list of installed games.
 *
 * Phase 4 — game-file import. The engine owns the whole install flow
 * (AppsListFragment -> InstallerDialog -> AppInstaller, all verified
 * at upstream tag 1.8.2; see docs/ENGINE-INTEGRATION.md §11), and
 * Home only routes intents into it:
 *
 * - ACTION_VIEW with a Uri (file/content/http/https .jar/.jad/.kjx):
 *   validated here, then forwarded to the engine MainActivity.
 * - ACTION_SEND (the engine has no SEND support at all: no filter,
 *   never reads EXTRA_STREAM): Home extracts the Uri from
 *   EXTRA_STREAM (single share, or the first item of a SEND_MULTIPLE)
 *   and forwards it as a VIEW data Uri to the engine MainActivity.
 * - "Nhập game từ file" button: SAF ACTION_OPEN_DOCUMENT ("*/*"),
 *   then the picked Uri is validated and forwarded the same way.
 *   History guard: a Uri restored from history
 *   (FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) is not forwarded again.
 * - Validation here is deliberately lenient: it only rejects what it
 *   can positively identify as wrong (an unsupported scheme, or a
 *   file extension that is present but not .jar/.jad/.kjx). A
 *   content:// Uri without an extension is forwarded — the engine
 *   sniffs the magic bytes (PK -> JAR, "KJX" -> KJX, else JAD) in
 *   FileUtils.getFileForUri. Everything the engine itself rejects
 *   (missing manifest, bad JAD, missing/corrupt JAR, duplicate
 *   handling) is reported by the engine's InstallerDialog, not here.
 * - A rejected intent shows our own Vietnamese error dialog and
 *   stays on Home; nothing is forwarded.
 *
 * The installed-games list is READ-ONLY here. It uses the engine's
 * own data layer the same way the engine fragment does:
 * `ViewModelProvider(this).get(AppListModel::class.java)` gives the
 * `AppRepository`, and `AppRepository.observeApps()` posts the list.
 * Tap a game -> `Config.startApp(title, pathExt, false)` (verified:
 * opens ConfigActivity on first run, MicroActivity afterwards).
 * Long-press a game -> our GameDetailsActivity.
 * Full library management (import from the file picker, search, sort,
 * rename, delete, reinstall, home-screen shortcut) stays in the
 * engine MainActivity ("Thư viện & Nhập game" button). Deliberately
 * not duplicated: the engine list is the single writer to the Room
 * database, and the work-directory first-run flow (permission request,
 * create-dir dialog) lives in the engine MainActivity.
 *
 * Two-repository staleness: this activity's AppListModel creates its
 * own AppRepository, and the engine MainActivity creates another one
 * when it runs (for example after installing a game). Room does not
 * propagate writes between two instances in the same process, so the
 * LiveData observed here can go stale. Mitigation: onResume re-queries
 * with `AppRepository.getAll().firstElement()` (verified public API)
 * whenever the work directory is ready.
 *
 * Work-directory readiness: `AppRepository` only opens its database
 * when `Config.getEmulatorDir()` is an existing writable directory
 * (verified in its constructor). If it is not ready at onCreate time,
 * this activity's repository stays database-less (getAll() would NPE),
 * so the list stays empty and the user is sent to the engine
 * MainActivity, which runs the real first-run flow. If the directory
 * becomes ready while this activity is paused, onResume restarts this
 * activity so a fresh AppListModel (and a fresh repository) is built.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var appRepository: AppRepository
    private lateinit var adapter: GameAdapter
    private val disposables = CompositeDisposable()
    private var dbReadyAtCreate = false

    /** SAF picker for "Nhập game từ file". OpenDocument (not
     * GetContent) so the returned Uri carries a persistable read
     * permission. "*/*": file providers report .jar/.jad with
     * unreliable MIME types, so filtering happens in
     * validateGameUri() and in the engine instead. */
    private val importFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> onImportFilePicked(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Game-file intent: validate, then forward to the engine (or
        // show our own error dialog and stay on Home).
        if (savedInstanceState == null && handleGameIntent(intent)) {
            finish()
            return
        }
        setContentView(R.layout.activity_home)

        findViewById<TextView>(R.id.home_work_dir).text = workDirText()

        findViewById<Button>(R.id.home_open_library).setOnClickListener {
            startActivity(Intent(this, EngineMainActivity::class.java))
        }
        findViewById<Button>(R.id.home_import_file).setOnClickListener {
            importFileLauncher.launch(arrayOf("*/*"))
        }
        findViewById<Button>(R.id.home_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<Button>(R.id.home_about).setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        adapter = GameAdapter()
        val listView = findViewById<ListView>(R.id.home_game_list)
        listView.adapter = adapter
        listView.emptyView = findViewById(R.id.home_empty)
        listView.setOnItemClickListener { _, _, position, _ ->
            val item = adapter.getItem(position) ?: return@setOnItemClickListener
            Config.startApp(this, item.title, item.pathExt, false)
        }
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val item = adapter.getItem(position) ?: return@setOnItemLongClickListener false
            GameDetailsActivity.launch(this, item)
            true
        }

        val appListModel = ViewModelProvider(this).get(AppListModel::class.java)
        appRepository = appListModel.appRepository
        dbReadyAtCreate = isWorkDirReady()
        appRepository.observeApps(this) { items ->
            adapter.setItems(items ?: emptyList())
        }
    }

    override fun onResume() {
        super.onResume()
        if (!dbReadyAtCreate) {
            // The work directory was not ready when this activity's
            // repository was built, so it has no database. If the
            // directory became ready meanwhile (the user went through
            // the engine's first-run flow), restart so a fresh
            // AppListModel builds a repository with a database.
            if (isWorkDirReady()) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            return
        }
        refreshGames()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Home is singleTask in the merged manifest, so a second
        // game-file intent while Home is running arrives here.
        if (handleGameIntent(intent)) {
            finish()
        }
    }

    override fun onDestroy() {
        disposables.clear()
        super.onDestroy()
    }

    /**
     * Handle a game-file intent (ACTION_VIEW / ACTION_SEND).
     * Returns true when the intent was forwarded to the engine (the
     * caller should finish Home); false when there was no game file
     * (show Home normally) or the file was rejected (an error dialog
     * was shown and Home stays open).
     */
    private fun handleGameIntent(source: Intent?): Boolean {
        val uri = gameUriFromIntent(source) ?: return false
        val error = validateGameUri(uri)
        if (error != null) {
            showImportError(error)
            return false
        }
        forwardToEngine(uri, source)
        return true
    }

    /** Extract the game-file Uri from an intent, or null when the
     * intent carries no game file. ACTION_VIEW carries it in data;
     * ACTION_SEND carries it in EXTRA_STREAM (the engine never reads
     * EXTRA_STREAM, so Home must). A Uri restored from history is
     * ignored, like the engine MainActivity does. */
    private fun gameUriFromIntent(source: Intent?): Uri? {
        if (source == null) return null
        if ((source.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0) {
            return null
        }
        return when (source.action) {
            Intent.ACTION_VIEW -> source.data
            Intent.ACTION_SEND ->
                source.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            Intent.ACTION_SEND_MULTIPLE ->
                source.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    ?.firstOrNull()
            else -> null
        }
    }

    /**
     * Lenient pre-check, only to give the user a clear Vietnamese
     * error for what is obviously not a game file. Returns an error
     * message, or null when the Uri may be a game file and the engine
     * should decide (it sniffs magic bytes for content:// and parses
     * the JAD/manifest itself).
     */
    private fun validateGameUri(uri: Uri): String? {
        val scheme = uri.scheme?.lowercase()
        if (scheme != "file" && scheme != "content"
            && scheme != "http" && scheme != "https"
        ) {
            return getString(R.string.import_error_bad_scheme)
        }
        val path = uri.path ?: ""
        val extension = path.substringAfterLast('.', "").lowercase()
        if (extension.isNotEmpty()
            && extension != "jar" && extension != "jad" && extension != "kjx"
        ) {
            return getString(R.string.import_error_bad_extension)
        }
        return null
    }

    /** Forward a validated game-file Uri to the engine MainActivity
     * as an ACTION_VIEW data Uri. URI-permission grant flags are
     * copied from the source intent so the engine can read a
     * content:// Uri it did not receive directly. */
    private fun forwardToEngine(uri: Uri, source: Intent?) {
        val target = Intent(Intent.ACTION_VIEW, uri).apply {
            setClass(this@MainActivity, EngineMainActivity::class.java)
            source?.let {
                val grantFlags = it.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                addFlags(grantFlags)
            }
        }
        startActivity(target)
    }

    /** SAF result for "Nhập game từ file". */
    private fun onImportFilePicked(uri: Uri?) {
        if (uri == null) return
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            // The picker did not grant a persistable permission; the
            // engine reads the file immediately, so continue anyway.
        }
        val error = validateGameUri(uri)
        if (error != null) {
            showImportError(error)
            return
        }
        forwardToEngine(uri, null)
    }

    private fun showImportError(message: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.import_error_title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun workDirText(): String {
        return try {
            getString(R.string.home_work_dir, Config.getEmulatorDir())
        } catch (e: Exception) {
            // Config's static init needs the engine Application to have
            // set ContextHolder; if that ever fails, Home must still open.
            getString(R.string.home_work_dir_unknown)
        }
    }

    private fun isWorkDirReady(): Boolean {
        return try {
            val dir = File(Config.getEmulatorDir())
            dir.isDirectory && dir.canWrite()
        } catch (e: Exception) {
            false
        }
    }

    /** Re-query the database once, to catch writes made through the
     * engine MainActivity's own repository instance (see class doc). */
    private fun refreshGames() {
        disposables.add(
            appRepository.all
                .firstElement()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { items -> adapter.setItems(items) },
                    { /* keep the LiveData list; next resume retries */ }
                )
        )
    }

    private inner class GameAdapter :
        ArrayAdapter<AppItem>(this@MainActivity, 0, mutableListOf()) {

        fun setItems(items: List<AppItem>) {
            clear()
            addAll(items)
            notifyDataSetChanged()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_game, parent, false)
            val item = getItem(position) ?: return view
            val iconView = view.findViewById<ImageView>(R.id.item_icon)
            val bitmap = AppUtils.getIconBitmap(item)
            if (bitmap != null) {
                iconView.setImageBitmap(bitmap)
            } else {
                iconView.setImageResource(android.R.drawable.sym_def_app_icon)
            }
            view.findViewById<TextView>(R.id.item_title).text = item.title
            val subtitle = "${item.author}  •  ${item.version}"
            view.findViewById<TextView>(R.id.item_subtitle).text = subtitle
            return view
        }
    }
}
