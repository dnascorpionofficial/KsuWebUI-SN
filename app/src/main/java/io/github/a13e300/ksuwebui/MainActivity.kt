package io.github.a13e300.ksuwebui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.os.Bundle
import android.os.Looper
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageButton
import android.widget.LinearLayout
import android.graphics.drawable.GradientDrawable
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import com.topjohnwu.superuser.nio.FileSystemManager
import io.github.a13e300.ksuwebui.databinding.ActivityMainBinding
import io.github.a13e300.ksuwebui.databinding.DialogConfirmBinding
import io.github.a13e300.ksuwebui.databinding.DialogInfoBinding
import io.github.a13e300.ksuwebui.databinding.ItemInfoHeaderBinding
import io.github.a13e300.ksuwebui.databinding.ItemInfoRowBinding
import io.github.a13e300.ksuwebui.databinding.ItemModuleBinding
import io.github.a13e300.ksuwebui.databinding.ItemPowerRowBinding
import io.github.a13e300.ksuwebui.databinding.ItemSheetActionBinding
import io.github.a13e300.ksuwebui.databinding.SheetPowerBinding
import io.github.a13e300.ksuwebui.databinding.SheetModuleSettingsBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.core.net.toUri
import androidx.core.content.edit
import androidx.drawerlayout.widget.DrawerLayout
import com.topjohnwu.superuser.Shell
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val KEY_OPT_BANNERS = "opt_banners"
private const val KEY_OPT_ICONS = "opt_icons"
private const val KEY_OPT_DISABLED = "opt_disabled"
private const val KEY_OPT_HIDDEN = "opt_hidden"
private const val KEY_PINNED = "pinned_modules"
private const val KEY_HIDDEN = "hidden_modules"
private const val KEY_PENDING_REBOOT = "pending_reboot"
private const val KEY_PENDING_BOOT = "pending_boot_id"
private const val DISABLED_RED = 0xFFE53935.toInt()
private const val REBOOT_CMD = "svc power reboot || reboot"
private const val SOFT_REBOOT_CMD = "setprop ctl.restart zygote || killall zygote"

@SuppressLint("NotifyDataSetChanged")
class MainActivity : AppCompatActivity(), FileSystemService.Listener {
    private lateinit var binding: ActivityMainBinding
    private var allModules = emptyList<Module>()
    private var moduleList = emptyList<Module>()
    private var searchQuery = ""
    private var imeWasVisible = false
    private var imeHiddenAt = 0L

    private val searchBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            val visible = ViewCompat.getRootWindowInsets(binding.root)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            val justHidden = (imeWasVisible && !visible) || SystemClock.uptimeMillis() - imeHiddenAt < 400
            imeWasVisible = visible
            when {
                visible -> WindowCompat.getInsetsController(window, binding.root)
                    .hide(WindowInsetsCompat.Type.ime())
                justHidden -> Unit
                else -> binding.toolbar.collapseActionView()
            }
        }
    }
    private val drawerBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = binding.drawer.closeDrawer(GravityCompat.START)
    }
    private lateinit var adapter: Adapter
    private val prefs by lazy { getSharedPreferences(PREFS_SETTINGS, MODE_PRIVATE) }
    private var shouldRefresh = false
    private var pickTarget: Pair<String, Boolean>? = null
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val (id, isBanner) = pickTarget ?: return@registerForActivityResult
        pickTarget = null
        if (uri == null) return@registerForActivityResult
        App.executor.execute {
            val bytes = try {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } catch (e: Exception) {
                null
            }
            if (bytes == null || bytes.size > 16 * 1024 * 1024) return@execute
            val src = decodeSampled(bytes, if (isBanner) 1080 else 192) ?: return@execute
            saveCustom(if (isBanner) "banner_$id.png" else "icon_$id.png", src)
            val shown = if (isBanner) blendBanner(src) else circleIcon(src)
            runOnUiThread {
                val i = allModules.indexOfFirst { it.id == id }
                if (i >= 0) {
                    val m = allModules[i]
                    allModules = allModules.toMutableList().also {
                        it[i] = if (isBanner) m.copy(banner = shown) else m.copy(icon = shown)
                    }
                    applyFilter()
                }
            }
        }
    }

    private fun stringSet(key: String): MutableSet<String> =
        prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()

    private fun setMember(key: String, id: String, on: Boolean) {
        val ids = stringSet(key)
        if (on) ids.add(id) else ids.remove(id)
        prefs.edit { putStringSet(key, ids) }
    }

    private fun togglePin(item: Module) {
        item.pinned = !item.pinned
        setMember(KEY_PINNED, item.id, item.pinned)
        applyFilter()
    }

    private fun toggleHidden(item: Module) {
        item.hidden = !item.hidden
        setMember(KEY_HIDDEN, item.id, item.hidden)
        applyFilter()
    }

    private fun toggleConsole(item: Module) {
        setMember(KEY_CONSOLE_MODULES, item.id, item.id !in stringSet(KEY_CONSOLE_MODULES))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.title = "${getString(R.string.app_name)} ${UpdateChecker.installedLabel()}"
        binding.toolbar.children.filterIsInstance<TextView>().firstOrNull()?.apply {
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
        }
        binding.toolbar.inflateMenu(R.menu.main_menu)
        binding.toolbar.setNavigationIcon(R.drawable.ic_menu)
        binding.toolbar.setNavigationContentDescription(R.string.open_menu)
        binding.toolbar.children.filterIsInstance<ImageButton>().firstOrNull()?.let { nav ->
            val d = resources.displayMetrics.density
            nav.minimumWidth = (48 * d).toInt()
            nav.setPaddingRelative((8 * d).toInt(), nav.paddingTop, 0, nav.paddingBottom)
        }
        binding.toolbar.setNavigationOnClickListener { binding.drawer.openDrawer(GravityCompat.START) }
        onBackPressedDispatcher.addCallback(this, searchBack)
        onBackPressedDispatcher.addCallback(this, drawerBack)

        lifecycleScope.launch(Dispatchers.IO) {
            AppList.getApps(this@MainActivity)
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val visible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (imeWasVisible && !visible) imeHiddenAt = SystemClock.uptimeMillis()
            imeWasVisible = visible
            ViewCompat.onApplyWindowInsets(v, insets)
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.appbar) { v, insets ->
            val cutoutAndBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(left = cutoutAndBars.left, top = cutoutAndBars.top, right = cutoutAndBars.right)
            return@setOnApplyWindowInsetsListener insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.list) { v, insets ->
            val cutoutAndBars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(left = cutoutAndBars.left, bottom = cutoutAndBars.bottom, right = cutoutAndBars.right)
            return@setOnApplyWindowInsetsListener insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.drawerPanel) { v, insets ->
            val b = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(left = b.left, top = b.top, bottom = b.bottom)
            insets
        }

        setupMenu(binding.toolbar.menu)
        setupDrawer()

        if (savedInstanceState == null) {
            lifecycleScope.launch(Dispatchers.IO) { UpdateChecker.cleanup() }
            UpdateChecker.available = UpdateChecker.cached()
            showUpdate(UpdateChecker.available)
            lifecycleScope.launch(Dispatchers.IO) {
                val found = UpdateChecker.check()
                UpdateChecker.available = found
                withContext(Dispatchers.Main) {
                    showUpdate(found)
                    if (found) showMessage(getString(R.string.update_title))
                }
            }
        } else {
            showUpdate(UpdateChecker.available)
        }

        adapter = Adapter()
        binding.list.adapter = adapter
        binding.list.setHasFixedSize(true)
        binding.list.setItemViewCacheSize(6)
        binding.list.recycledViewPool.setMaxRecycledViews(0, 10)
        prewarmCards()
        binding.swipeRefresh.setOnRefreshListener {
            refresh()
        }
        refresh()
    }

    private fun prewarmCards() {
        val pool = binding.list.recycledViewPool
        var left = 6
        Looper.myQueue().addIdleHandler {
            if (isDestroyed) return@addIdleHandler false
            pool.putRecycledView(adapter.createViewHolder(binding.list, 0))
            --left > 0
        }
    }

    private fun setupMenu(menu: Menu) {
        val searchItem = menu.findItem(R.id.search)
        searchItem.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                searchBack.isEnabled = true
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                searchBack.isEnabled = false
                searchQuery = ""
                applyFilter()
                return true
            }
        })
        (searchItem.actionView as SearchView).apply {
            queryHint = getString(R.string.search_modules)
            maxWidth = Int.MAX_VALUE
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?) = false
                override fun onQueryTextChange(newText: String?): Boolean {
                    searchQuery = newText.orEmpty()
                    applyFilter()
                    return true
                }
            })
        }
        menu.findItem(R.id.update).actionView?.setOnClickListener {
            if (UpdateChecker.available && !fetchingRelease) {
                fetchingRelease = true
                lifecycleScope.launch {
                    val update = withContext(Dispatchers.IO) { UpdateChecker.fetchRelease() }
                    fetchingRelease = false
                    if (update != null) showUpdateDialog(update) else showMessage(UpdateChecker.status)
                }
            }
        }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.power -> showPowerSheet()
                else -> return@setOnMenuItemClickListener false
            }
            true
        }
    }

    private class PowerOption(val icon: Int, val label: Int, val desc: Int, val message: Int, val cmd: String)

    private fun showPowerSheet() {
        val options = listOf(
            PowerOption(R.drawable.ic_reboot, R.string.reboot, R.string.reboot_desc, R.string.confirm_reboot, REBOOT_CMD),
            PowerOption(R.drawable.ic_soft_reboot, R.string.soft_reboot, R.string.soft_reboot_desc, R.string.confirm_soft_reboot, SOFT_REBOOT_CMD),
            PowerOption(R.drawable.ic_recovery, R.string.reboot_recovery, R.string.reboot_recovery_desc, R.string.confirm_reboot_recovery, "svc power reboot recovery || reboot recovery"),
            PowerOption(R.drawable.ic_bootloader, R.string.reboot_bootloader, R.string.reboot_bootloader_desc, R.string.confirm_reboot_bootloader, "svc power reboot bootloader || reboot bootloader"),
            PowerOption(R.drawable.ic_safe_mode, R.string.reboot_safe_mode, R.string.reboot_safe_mode_desc, R.string.confirm_reboot_safe_mode, "setprop persist.sys.safemode 1; $REBOOT_CMD")
        )
        val dialog = BottomSheetDialog(this)
        val sheet = SheetPowerBinding.inflate(layoutInflater)
        val density = resources.displayMetrics.density
        options.forEachIndexed { index, option ->
            if (index > 0) {
                val divider = View(this)
                divider.layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, (1 * density).toInt()
                ).apply { marginStart = (76 * density).toInt() }
                divider.setBackgroundColor(ContextCompat.getColor(this, R.color.divider))
                sheet.list.addView(divider)
            }
            val row = ItemPowerRowBinding.inflate(layoutInflater, sheet.list, false)
            row.icon.setImageResource(option.icon)
            row.label.setText(option.label)
            row.desc.setText(option.desc)
            row.root.setOnClickListener {
                dialog.dismiss()
                confirmPower(option)
            }
            sheet.list.addView(row.root)
        }
        dialog.setContentView(sheet.root)
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.show()
    }

    private fun confirmPower(option: PowerOption) {
        showConfirm(option.icon, option.label, option.message) {
            App.executor.execute { Shell.cmd(option.cmd).exec() }
        }
    }

    private fun showConfirm(icon: Int?, title: Int, message: Int, onYes: () -> Unit): Unit =
        showConfirm(icon, getString(title), getString(message), onYes = onYes)

    private fun showConfirm(
        icon: Int?, title: CharSequence, message: CharSequence,
        yes: Int = R.string.yes, no: Int = R.string.no, onYes: () -> Unit
    ) {
        val view = DialogConfirmBinding.inflate(layoutInflater)
        if (icon != null) {
            view.icon.setImageResource(icon)
            view.icon.visibility = View.VISIBLE
        }
        view.title.text = title
        view.message.text = message
        view.yes.setText(yes)
        view.no.setText(no)
        val dialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_KsuWebUI_ButtonDialog)
            .setView(view.root)
            .create()
        view.no.setOnClickListener { dialog.dismiss() }
        view.yes.setOnClickListener {
            dialog.dismiss()
            onYes()
        }
        dialog.show()
    }

    private fun setupDrawer() {
        binding.drawer.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                drawerBack.isEnabled = true
            }

            override fun onDrawerClosed(drawerView: View) {
                drawerBack.isEnabled = false
            }
        })
        binding.themeGroup.check(
            when (themeMode(prefs)) {
                THEME_SYSTEM -> R.id.btn_system
                THEME_DARK -> R.id.btn_dark
                else -> R.id.btn_light
            }
        )
        binding.themeGroup.addOnButtonCheckedListener { _, id, checked ->
            val mode = when (id) {
                R.id.btn_system -> THEME_SYSTEM
                R.id.btn_dark -> THEME_DARK
                else -> THEME_LIGHT
            }
            if (!checked || mode == themeMode(prefs)) return@addOnButtonCheckedListener
            prefs.edit { putInt("theme_mode", mode) }
            AppCompatDelegate.setDefaultNightMode(nightMode(mode))
        }
        binding.drawerInfo.setOnClickListener { showInfoDialog() }
        binding.swBanners.bindPref(KEY_OPT_BANNERS, true) { refresh() }
        binding.swIcons.bindPref(KEY_OPT_ICONS, true) { refresh() }
        binding.swDisabled.bindPref(KEY_OPT_DISABLED, true) { refresh() }
        binding.swHidden.bindPref(KEY_OPT_HIDDEN, false) { applyFilter() }
    }

    private fun CompoundButton.bindPref(key: String, default: Boolean, onChange: (Boolean) -> Unit = {}) {
        isChecked = prefs.getBoolean(key, default)
        setOnCheckedChangeListener { _, on ->
            prefs.edit { putBoolean(key, on) }
            onChange(on)
        }
    }

    private var fetchingRelease = false

    private fun showUpdate(visible: Boolean) {
        binding.toolbar.menu.findItem(R.id.update)?.isVisible = visible
    }

    override fun onResume() {
        super.onResume()
        if (shouldRefresh) {
            refresh()
            shouldRefresh = false
        }
    }

    private fun refresh() {
        binding.swipeRefresh.isRefreshing = true
        allModules = emptyList()
        moduleList = emptyList()
        adapter.notifyDataSetChanged()
        binding.info.setText(R.string.loading)
        binding.info.isVisible = true
        FileSystemService.start(this)
    }

    override fun onServiceAvailable(fs: FileSystemManager) {
        App.executor.submit {
            val mods = mutableListOf<Module>()
            val showDisabled = prefs.getBoolean(KEY_OPT_DISABLED, true)
            val showBanners = prefs.getBoolean(KEY_OPT_BANNERS, true)
            val showIcons = prefs.getBoolean(KEY_OPT_ICONS, true)
            val sizes = HashMap<String, Long>()
            try {
                Shell.cmd("du -sk /data/adb/modules/*").exec().out.forEach { l ->
                    val p = l.trim().split(Regex("\\s+"), limit = 2)
                    if (p.size == 2) p[0].toLongOrNull()?.let { sizes[p[1].substringAfterLast('/')] = it }
                }
            } catch (e: Exception) {
            }
            fs.getFile("/data/adb/modules").listFiles()!!.forEach { f ->
                if (!f.isDirectory) return@forEach
                if (!fs.getFile(f, "webroot").isDirectory) return@forEach
                if (!fs.getFile(f, "module.prop").exists()) return@forEach
                val disabled = fs.getFile(f, "disable").exists()
                if (disabled && !showDisabled) return@forEach
                var name = f.name
                val id = f.name
                var author = "?"
                var version = "?"
                var versionCode = ""
                var updateJson = ""
                var desc = ""
                var bannerRaw: String? = null
                var coverRaw: String? = null
                fs.getFile(f, "module.prop").newInputStream().bufferedReader().use {
                    it.lines().forEach { l ->
                        val ls = l.split("=", limit = 2)
                        if (ls.size == 2) {
                            when (ls[0]) {
                                "name" -> name = ls[1]
                                "description" -> desc = ls[1]
                                "author" -> author = ls[1]
                                "version" -> version = ls[1]
                                "versionCode" -> versionCode = ls[1].trim()
                                "updateJson" -> updateJson = ls[1].trim()
                                "banner" -> bannerRaw = ls[1]
                                "cover" -> coverRaw = ls[1]
                            }
                        }
                    }
                }
                val banner = if (showBanners) loadBanner(fs, f.path, bannerRaw ?: coverRaw, id) else null
                val icon = if (showIcons) loadIcon(fs, f.path, id) else null
                val modified = try {
                    fs.getFile("${f.path}/webroot/index.html").lastModified()
                } catch (e: Exception) {
                    0L
                }
                val size = sizes[id]?.let { formatSize(it) } ?: ""
                val date = if (modified > 0) {
                    SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(modified))
                } else {
                    ""
                }
                mods.add(Module(name, id, desc, author, version, banner, icon, size, date, disabled, versionCode = versionCode, updateJson = updateJson))
            }
            val pinnedIds = stringSet(KEY_PINNED)
            val hiddenIds = stringSet(KEY_HIDDEN)
            mods.forEach {
                it.pinned = it.id in pinnedIds
                it.hidden = it.id in hiddenIds
            }
            runOnUiThread {
                allModules = mods
                applyFilter()
                binding.swipeRefresh.isRefreshing = false
                if (mods.isEmpty()) {
                    binding.info.setText(R.string.no_modules)
                    binding.info.isVisible = true
                }
                checkModuleUpdates(mods)
            }
        }
    }

    private fun formatSize(kb: Long): String =
        if (kb >= 1024) String.format(Locale.getDefault(), "%.2f MB", kb / 1024.0) else "$kb KB"

    private fun readRemote(fs: FileSystemManager, path: String, max: Long): ByteArray? = try {
        val f = fs.getFile(path)
        if (!f.isFile || f.length() > max) null else f.newInputStream().use { it.readBytes() }
    } catch (e: Exception) {
        null
    }

    private fun decodeSampled(bytes: ByteArray, maxW: Int): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxW) sample *= 2
        BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    } catch (e: Exception) {
        null
    }

    private fun toGpu(bmp: Bitmap): Bitmap =
        bmp.copy(Bitmap.Config.HARDWARE, false)?.also { bmp.recycle() } ?: bmp

    private fun customFile(name: String) = File(File(filesDir, "custom"), name)

    private fun saveCustom(name: String, bmp: Bitmap) {
        val f = customFile(name)
        f.parentFile?.mkdirs()
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun loadBanner(fs: FileSystemManager, moduleDir: String, raw: String?, id: String): Bitmap? {
        val custom = customFile("banner_$id.png")
        val bytes: ByteArray? = if (custom.isFile) {
            custom.readBytes()
        } else {
            val v = raw?.trim()?.trim('"', '\'')?.replace("<MODULECWD>", moduleDir)
            if (v.isNullOrEmpty() || v.startsWith("http", ignoreCase = true)) null
            else readRemote(fs, if (v.startsWith("/")) v else "$moduleDir/$v", 8L * 1024 * 1024)
        }
        return bytes?.let { decodeSampled(it, 1080) }?.let { blendBanner(it) }
    }

    private fun loadIcon(fs: FileSystemManager, moduleDir: String, id: String): Bitmap? {
        val custom = customFile("icon_$id.png")
        val bytes: ByteArray? = if (custom.isFile) {
            custom.readBytes()
        } else {
            readRemote(fs, "$moduleDir/webroot/icon.png", 2L * 1024 * 1024)
                ?: readRemote(fs, "$moduleDir/webroot/icon.jpg", 2L * 1024 * 1024)
        }
        return bytes?.let { decodeSampled(it, 192) }?.let { circleIcon(it) }
    }

    private fun circleIcon(src: Bitmap): Bitmap {
        val size = 192
        val side = minOf(src.width, src.height)
        val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, size, size, true)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        Canvas(out).drawCircle(size / 2f, size / 2f, size / 2f, paint)
        return toGpu(out)
    }

    private fun blendBanner(src: Bitmap): Bitmap {
        var cw = src.width
        var ch = (cw / 2.4f).toInt()
        if (ch > src.height) {
            ch = src.height
            cw = (ch * 2.4f).toInt()
        }
        val scale = minOf(1f, resources.displayMetrics.widthPixels / cw.toFloat())
        val cropped = Bitmap.createBitmap(
            src, (src.width - cw) / 2, (src.height - ch) / 2, cw, ch,
            Matrix().apply { setScale(scale, scale) }, true
        )
        val wf = cropped.width.toFloat()
        val hf = cropped.height.toFloat()

        val out = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(cropped, 0f, 0f, null)
        c.drawRect(0f, 0f, wf, hf, Paint().apply {
            shader = LinearGradient(0f, hf * 0.75f, 0f, hf, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        })
        return toGpu(out)
    }

    private val moduleOrder = compareByDescending<Module> { it.pinned }
        .thenByDescending { it.banner != null }
        .thenBy { it.name.lowercase() }

    private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun applyFilter() {
        val q = normalize(searchQuery)
        val showHidden = prefs.getBoolean(KEY_OPT_HIDDEN, false)
        val visible = if (showHidden) allModules else allModules.filter { !it.hidden }
        moduleList = (if (q.isEmpty()) visible else visible.filter {
            normalize(it.name).contains(q)
        }).sortedWith(moduleOrder)
        adapter.notifyDataSetChanged()
        if (allModules.isNotEmpty()) {
            binding.info.setText(R.string.no_results)
            binding.info.isVisible = moduleList.isEmpty()
        }
    }

    private fun showMessage(text: CharSequence) {
        val root = binding.root
        val bar = Snackbar.make(root, text, Snackbar.LENGTH_LONG)
        val density = resources.displayMetrics.density
        bar.view.background = GradientDrawable().apply {
            cornerRadius = 16 * density
            setColor(MaterialColors.getColor(root, com.google.android.material.R.attr.colorSurfaceContainerHigh))
            setStroke(
                (1 * density).toInt(),
                MaterialColors.getColor(root, com.google.android.material.R.attr.colorOutlineVariant)
            )
        }
        bar.view.backgroundTintList = null
        bar.setTextColor(MaterialColors.getColor(root, com.google.android.material.R.attr.colorOnSurface))
        bar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).maxLines = 4
        bar.show()
    }

    private fun showModuleSettings(item: Module) {
        val dialog = BottomSheetDialog(this)
        val sheet = SheetModuleSettingsBinding.inflate(layoutInflater)
        sheet.title.text = item.name

        fun action(icon: Int, label: Int, run: () -> Unit) {
            val row = ItemSheetActionBinding.inflate(layoutInflater, sheet.actions, false)
            row.icon.setImageResource(icon)
            row.label.setText(label)
            row.root.setOnClickListener {
                dialog.dismiss()
                run()
            }
            sheet.actions.addView(row.root)
        }

        action(R.drawable.ic_push_pin, if (item.pinned) R.string.unpin else R.string.pin) { togglePin(item) }
        val showBanners = prefs.getBoolean(KEY_OPT_BANNERS, true)
        val showIcons = prefs.getBoolean(KEY_OPT_ICONS, true)
        val bannerFile = customFile("banner_${item.id}.png")
        val iconFile = customFile("icon_${item.id}.png")
        if (showBanners) {
            action(R.drawable.ic_image, R.string.set_background) {
                pickTarget = item.id to true
                pickImage.launch("image/*")
            }
        }
        if (showIcons) {
            action(R.drawable.ic_account_circle, R.string.set_icon) {
                pickTarget = item.id to false
                pickImage.launch("image/*")
            }
        }
        if (showBanners && bannerFile.exists()) action(R.drawable.ic_delete, R.string.remove_background) {
            bannerFile.delete()
            refresh()
        }
        if (showIcons && iconFile.exists()) action(R.drawable.ic_delete, R.string.remove_icon) {
            iconFile.delete()
            refresh()
        }
        action(R.drawable.ic_add, R.string.add_to_home) { addToHomeScreen(item) }
        action(
            if (item.hidden) R.drawable.ic_visibility else R.drawable.ic_visibility_off,
            if (item.hidden) R.string.unhide else R.string.hide
        ) { toggleHidden(item) }
        action(
            R.drawable.ic_terminal,
            if (item.id in stringSet(KEY_CONSOLE_MODULES)) R.string.console_disable else R.string.console_enable
        ) { toggleConsole(item) }
        action(R.drawable.ic_delete, R.string.clear_module_data) {
            showConfirm(R.drawable.ic_delete, R.string.clear_module_data, R.string.clear_module_data_message) {
                clearModuleData(item.id) { showMessage(getString(R.string.module_data_cleared)) }
            }
        }

        dialog.setContentView(sheet.root)
        dialog.behavior.skipCollapsed = true
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.show()
    }

    private fun addToHomeScreen(item: Module) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            showMessage(getString(R.string.shortcut_unsupported))
            return
        }
        val intent = Intent(this, WebUIActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData("ksuwebui://webui/${item.id}".toUri())
            .putExtra("id", item.id)
            .putExtra("name", item.name)
        val info = ShortcutInfoCompat.Builder(this, "module_${item.id}")
            .setShortLabel(item.name)
            .setLongLabel(item.name)
            .setIcon(shortcutIcon(item))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.requestPinShortcut(this, info, null)
    }

    private fun shortcutIcon(item: Module): IconCompat {
        val src = item.icon?.copy(Bitmap.Config.ARGB_8888, false)
            ?: ContextCompat.getDrawable(this, R.drawable.ic_module_default)!!.toBitmap(192, 192)
        val size = 432
        val inset = size / 6
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawBitmap(src, null, Rect(inset, inset, size - inset, size - inset), Paint(Paint.FILTER_BITMAP_FLAG))
        return IconCompat.createWithAdaptiveBitmap(bmp)
    }

    override fun onLaunchFailed() {
        allModules = emptyList()
        moduleList = emptyList()
        adapter.notifyDataSetChanged()
        binding.info.setText(R.string.please_grant_root)
        binding.info.isVisible = true
        binding.swipeRefresh.isRefreshing = false
    }

    data class Module(val name: String, val id: String, val desc: String, val author: String, val version: String, val banner: Bitmap? = null, val icon: Bitmap? = null, val size: String = "", val date: String = "", val disabled: Boolean = false, var pinned: Boolean = false, var hidden: Boolean = false, val versionCode: String = "", val updateJson: String = "", val updateAvailable: Boolean = false)

    class ViewHolder(val binding: ItemModuleBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            val consumeTouch = View.OnTouchListener { _, _ -> true }
            listOf(
                binding.size, binding.date, binding.moduleUpdate,
                binding.statusDisabled, binding.statusHidden, binding.statusUpdate
            ).forEach { it.setOnTouchListener(consumeTouch) }
        }
    }

    inner class Adapter : RecyclerView.Adapter<ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(
                ItemModuleBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )
        }

        override fun getItemCount(): Int = moduleList.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = moduleList[position]
            val id = item.id
            val name = item.name
            holder.binding.name.text = name
            holder.binding.author.text = resources.getString(R.string.author, item.author)
            holder.binding.version.text = resources.getString(R.string.version, item.version)
            holder.binding.versionCode.text = resources.getString(R.string.version_code, item.versionCode)
            holder.binding.versionCode.isVisible = item.versionCode.isNotEmpty()
            holder.binding.size.text = item.size
            holder.binding.size.isVisible = item.size.isNotEmpty()
            holder.binding.toggle.setOnCheckedChangeListener(null)
            holder.binding.toggle.isEnabled = true
            holder.binding.toggle.isChecked = !item.disabled
            holder.binding.toggle.setOnCheckedChangeListener { button, on ->
                button.isEnabled = false
                setModuleEnabled(item, on)
            }
            val statusCount = listOf(item.disabled, item.hidden, item.updateAvailable).count { it }
            val multiple = statusCount > 1
            holder.binding.moduleUpdate.isVisible = item.updateAvailable && !multiple
            holder.binding.statusDisabled.isVisible = item.disabled && multiple
            holder.binding.statusHidden.isVisible = item.hidden && multiple
            holder.binding.statusUpdate.isVisible = item.updateAvailable && multiple
            if (statusCount == 0) {
                holder.binding.date.text = item.date
                holder.binding.date.setTextColor(
                    MaterialColors.getColor(holder.binding.date, com.google.android.material.R.attr.colorOnSurfaceVariant)
                )
                holder.binding.date.isVisible = item.date.isNotEmpty()
            } else if (!multiple && !item.updateAvailable) {
                holder.binding.date.text = getString(if (item.disabled) R.string.module_disabled else R.string.module_hidden)
                holder.binding.date.setTextColor(DISABLED_RED)
                holder.binding.date.isVisible = true
            } else {
                holder.binding.date.isVisible = false
            }
            holder.binding.desc.text = item.desc
            holder.binding.banner.setImageBitmap(item.banner)
            holder.binding.banner.isVisible = item.banner != null
            val iconsOn = prefs.getBoolean(KEY_OPT_ICONS, true)
            holder.binding.icon.isVisible = iconsOn
            if (item.icon != null) holder.binding.icon.setImageBitmap(item.icon)
            else holder.binding.icon.setImageResource(R.drawable.ic_module_default)
            holder.binding.settings.setOnClickListener { showModuleSettings(item) }
            holder.binding.name.setCompoundDrawablesRelativeWithIntrinsicBounds(
                0, 0, if (item.pinned) R.drawable.ic_push_pin else 0, 0
            )
            holder.binding.root.setOnClickListener {
                if (item.disabled) {
                    showConfirm(null, R.string.module_disabled, R.string.confirm_open_disabled) { openModule(id, name) }
                } else {
                    openModule(id, name)
                }
            }
        }

    }

    private fun showInfoDialog() {
        val dialog = DialogInfoBinding.inflate(layoutInflater)
        val neutral = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurfaceVariant)
        fun header(title: Int) {
            val h = ItemInfoHeaderBinding.inflate(layoutInflater, dialog.content, false)
            h.root.setText(title)
            dialog.content.addView(h.root)
        }
        fun row(title: Int, body: Int, icon: Int = 0, tint: Int = neutral) {
            val r = ItemInfoRowBinding.inflate(layoutInflater, dialog.content, false)
            if (title != 0) r.title.setText(title) else r.title.isVisible = false
            r.body.setText(body)
            if (icon != 0) {
                r.icon.setImageResource(icon)
                r.icon.imageTintList = ColorStateList.valueOf(tint)
            } else {
                r.icon.isVisible = false
            }
            dialog.content.addView(r.root)
        }
        header(R.string.settings)
        row(R.string.info_theme_title, R.string.info_theme_body)
        row(R.string.opt_banners, R.string.info_banners_body)
        row(R.string.opt_icons, R.string.info_icons_body)
        row(R.string.opt_disabled, R.string.info_disabled_body)
        row(R.string.opt_hidden, R.string.info_hidden_body)
        header(R.string.info_section_cards)
        row(0, R.string.info_status_note)
        row(R.string.module_disabled, R.string.info_status_disabled_body, R.drawable.ic_block, ContextCompat.getColor(this, R.color.status_red))
        row(R.string.module_hidden, R.string.info_status_hidden_body, R.drawable.ic_visibility_off, ContextCompat.getColor(this, R.color.status_red))
        row(R.string.module_update_available, R.string.info_status_update_body, R.drawable.ic_update, ContextCompat.getColor(this, R.color.update_chip))
        row(R.string.info_pinned_title, R.string.info_pinned_body, R.drawable.ic_push_pin)
        row(R.string.module_settings, R.string.info_gear_body, R.drawable.ic_settings)
        row(R.string.module_toggle, R.string.info_switch_body, R.drawable.ic_power)
        MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_KsuWebUI_ButtonDialog)
            .setView(dialog.root)
            .setPositiveButton(R.string.info_close, null)
            .show()
    }

    private fun checkModuleUpdates(mods: List<Module>) {
        val targets = mods.filter { it.versionCode.toLongOrNull() != null && it.updateJson.startsWith("http", ignoreCase = true) }
        if (targets.isEmpty()) return
        lifecycleScope.launch(Dispatchers.IO) {
            val gate = Semaphore(4)
            targets.map { m ->
                launch {
                    gate.withPermit {
                        val latest = ModuleUpdateChecker.latestVersionCode(m.updateJson) ?: return@withPermit
                        if (latest > m.versionCode.toLong()) {
                            withContext(Dispatchers.Main) { setModuleUpdate(m.id) }
                        }
                    }
                }
            }.joinAll()
        }
    }

    private fun setModuleUpdate(id: String) {
        val i = allModules.indexOfFirst { it.id == id }
        if (i < 0) return
        allModules = allModules.toMutableList().also { it[i] = it[i].copy(updateAvailable = true) }
        applyFilter()
    }

    private fun setModuleEnabled(item: Module, enable: Boolean) {
        val marker = "'/data/adb/modules/${item.id.replace("'", "'\\''")}/disable'"
        App.executor.execute {
            val ok = Shell.cmd(if (enable) "rm -f $marker" else "touch $marker").exec().isSuccess
            val needsReboot = ok && markPendingReboot(item.id)
            runOnUiThread {
                if (!ok) {
                    showMessage(getString(R.string.module_toggle_failed))
                } else {
                    val i = allModules.indexOfFirst { it.id == item.id }
                    if (i >= 0) allModules = allModules.toMutableList().also { it[i] = it[i].copy(disabled = !enable) }
                }
                applyFilter()
                if (needsReboot) showRebootPrompt(enable)
            }
        }
    }

    private fun markPendingReboot(id: String): Boolean {
        val boot = Shell.cmd("cat /proc/sys/kernel/random/boot_id").exec().out.firstOrNull()?.trim().orEmpty()
        if (boot.isEmpty()) return true
        if (prefs.getString(KEY_PENDING_BOOT, null) != boot) {
            prefs.edit {
                putString(KEY_PENDING_BOOT, boot)
                remove(KEY_PENDING_REBOOT)
            }
        }
        val pending = stringSet(KEY_PENDING_REBOOT)
        val needed = pending.add(id)
        if (!needed) pending.remove(id)
        prefs.edit { putStringSet(KEY_PENDING_REBOOT, pending) }
        return needed
    }

    private fun showRebootPrompt(enabled: Boolean) {
        showConfirm(
            R.drawable.ic_reboot,
            getString(if (enabled) R.string.reboot_to_enable else R.string.reboot_to_disable),
            getString(R.string.reboot_to_apply_message),
            R.string.reboot_now, R.string.update_later
        ) {
            App.executor.execute { Shell.cmd(REBOOT_CMD).exec() }
        }
    }

    private fun openModule(id: String, name: String) {
        shouldRefresh = true
        startActivity(
            Intent(this, WebUIActivity::class.java)
                .setData("ksuwebui://webui/$id".toUri())
                .putExtra("id", id)
                .putExtra("name", name)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        FileSystemService.removeListener(this)
    }
}
