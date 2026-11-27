package com.khahdihdz.j2meemulator

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
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
 * (MAIN/LAUNCHER + the ACTION_VIEW filters, owned by this activity in
 * the merged manifest; its theme there is Theme.J2MEEmulator, light
 * blue/white). It is also the Splash: the first thing the user sees is
 * the app name, version and work directory on a white card, then the
 * list of installed games.
 *
 * What this screen does and does not do (all engine behaviour below was
 * verified at upstream tag 1.8.2; see docs/ENGINE-INTEGRATION.md §10):
 *
 * - Game file intents (ACTION_VIEW / ACTION_SEND with a Uri) are still
 *   forwarded immediately to the engine MainActivity, exactly like the
 *   Phase 2 forwarder did. The engine owns the whole import flow
 *   (AppsListFragment -> InstallerDialog). History guard: a Uri
 *   restored from history (FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) is not
 *   forwarded again.
 * - The installed-games list is READ-ONLY here. It uses the engine's
 *   own data layer the same way the engine fragment does:
 *   `ViewModelProvider(this).get(AppListModel::class.java)` gives the
 *   `AppRepository`, and `AppRepository.observeApps()` posts the list.
 *   Tap a game -> `Config.startApp(title, pathExt, false)` (verified:
 *   opens ConfigActivity on first run, MicroActivity afterwards).
 *   Long-press a game -> our GameDetailsActivity.
 * - Full library management (import from the file picker, search, sort,
 *   rename, delete, reinstall, home-screen shortcut) stays in the
 *   engine MainActivity ("Thư viện & Nhập game" button). Deliberately
 *   not duplicated: the engine list is the single writer to the Room
 *   database, and the work-directory first-run flow (permission request,
 *   create-dir dialog) lives in the engine MainActivity.
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Game-file intent: forward to the engine, do not show Home.
        if (savedInstanceState == null && shouldForward(intent)) {
            forwardToEngine(intent)
            finish()
            return
        }
        setContentView(R.layout.activity_home)

        findViewById<TextView>(R.id.home_work_dir).text = workDirText()

        findViewById<Button>(R.id.home_open_library).setOnClickListener {
            startActivity(Intent(this, EngineMainActivity::class.java))
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

    override fun onDestroy() {
        disposables.clear()
        super.onDestroy()
    }

    private fun shouldForward(source: Intent?): Boolean {
        if (source?.data == null) return false
        return (source.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0
    }

    private fun forwardToEngine(source: Intent?) {
        val target = Intent(this, EngineMainActivity::class.java)
        if (source != null) {
            source.data?.let { target.data = it }
            target.action = source.action
        }
        startActivity(target)
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
