package io.github.a13e300.ksuwebui

import android.annotation.SuppressLint
import android.content.Intent
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
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageButton
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.topjohnwu.superuser.nio.FileSystemManager
import io.github.a13e300.ksuwebui.databinding.ActivityMainBinding
import io.github.a13e300.ksuwebui.databinding.DialogConfirmBinding
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
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
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

    private fun getPinnedModules(): MutableSet<String> {
        return prefs.getStringSet("pinned_modules", emptySet<String>())?.toMutableSet() ?: mutableSetOf()
    }

    private fun savePinnedModules(pinned: Set<String>) {
        prefs.edit { putStringSet("pinned_modules", pinned) }
    }

    private fun getHiddenModules(): MutableSet<String> {
        return prefs.getStringSet("hidden_modules", emptySet<String>())?.toMutableSet() ?: mutableSetOf()
    }

    private fun getConsoleModules(): MutableSet<String> {
        return prefs.getStringSet("console_modules", emptySet<String>())?.toMutableSet() ?: mutableSetOf()
    }

    private fun toggleHidden(item: Module) {
        item.hidden = !item.hidden
        val ids = getHiddenModules()
        if (item.hidden) ids.add(item.id) else ids.remove(item.id)
        prefs.edit { putStringSet("hidden_modules", ids) }
        applyFilter()
    }

    private fun toggleConsole(item: Module) {
        val ids = getConsoleModules()
        if (!ids.add(item.id)) ids.remove(item.id)
        prefs.edit { putStringSet("console_modules", ids) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbar.title = "${getString(R.string.app_name)} ${BuildConfig.VERSION_NAME}-${BuildConfig.BUILD_SUFFIX}"
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
        }

        if (savedInstanceState == null) {
            UpdateChecker.available = UpdateChecker.cached()
            showUpdate(UpdateChecker.available)
            lifecycleScope.launch(Dispatchers.IO) {
                val code = UpdateChecker.check()
                UpdateChecker.available = code
                withContext(Dispatchers.Main) {
                    showUpdate(code)
                    if (code != null) showMessage(getString(R.string.update_title))
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
        binding.swipeRefresh.setOnRefreshListener {
            refresh()
        }
        refresh()
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
            if (UpdateChecker.available != null && !fetchingRelease) {
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
                divider.layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, (1 * density).toInt()
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

    private fun showConfirm(icon: Int?, title: Int, message: Int, onYes: () -> Unit) {
        val view = DialogConfirmBinding.inflate(layoutInflater)
        if (icon != null) {
            view.icon.setImageResource(icon)
            view.icon.visibility = View.VISIBLE
        }
        view.title.setText(title)
        view.message.setText(message)
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
        binding.swShowBanners.bindPref("show_banners", true) { refresh() }
        binding.swShowIcons.bindPref("show_icons", true) { refresh() }
        binding.swShowDisabled.bindPref("show_disabled", true) { refresh() }
        binding.swShowHidden.bindPref("show_hidden", false) { applyFilter() }
    }

    private fun CompoundButton.bindPref(key: String, default: Boolean, onChange: (Boolean) -> Unit = {}) {
        isChecked = prefs.getBoolean(key, default)
        setOnCheckedChangeListener { _, on ->
            prefs.edit { putBoolean(key, on) }
            onChange(on)
        }
    }

    private var fetchingRelease = false

    private fun showUpdate(code: Int?) {
        binding.toolbar.menu.findItem(R.id.update)?.isVisible = code != null
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
            val showDisabled = prefs.getBoolean("show_disabled", true)
            val showBanners = prefs.getBoolean("show_banners", true)
            val showIcons = prefs.getBoolean("show_icons", true)
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
                if (fs.getFile(f, "disable").exists() && !showDisabled) return@forEach
                var name = f.name
                val id = f.name
                var author = "?"
                var version = "?"
                var desc = ""
                var bannerRaw: String? = null
                var coverRaw: String? = null
                fs.getFile(f, "module.prop").newInputStream().bufferedReader().use {
                    it.lines().forEach { l ->
                        val ls = l.split("=", limit = 2)
                        if (ls.size == 2) {
                            if (ls[0] == "name") name = ls[1]
                            else if (ls[0] == "description") desc = ls[1]
                            else if (ls[0] == "author") author = ls[1]
                            else if (ls[0] == "version") version = ls[1]
                            else if (ls[0] == "banner") bannerRaw = ls[1]
                            else if (ls[0] == "cover") coverRaw = ls[1]
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
                val disabled = fs.getFile(f, "disable").exists()
                mods.add(Module(name, id, desc, author, version, banner, icon, size, date, disabled))
            }
            val pinnedIds = getPinnedModules()
            val hiddenIds = getHiddenModules()
            mods.forEach {
                it.pinned = it.id in pinnedIds
                it.hidden = it.id in hiddenIds
            }
            mods.sortWith(moduleOrder)
            runOnUiThread {
                allModules = mods
                applyFilter()
                binding.swipeRefresh.isRefreshing = false
                if (mods.isEmpty()) {
                    binding.info.setText(R.string.no_modules)
                    binding.info.isVisible = true
                }
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

    private fun togglePin(item: Module) {
        item.pinned = !item.pinned
        val pinnedIds = getPinnedModules()
        if (item.pinned) {
            pinnedIds.add(item.id)
        } else {
            pinnedIds.remove(item.id)
        }
        savePinnedModules(pinnedIds)
        applyFilter()
    }

    private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun applyFilter() {
        val q = normalize(searchQuery)
        val showHidden = prefs.getBoolean("show_hidden", false)
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
        if (prefs.getBoolean("show_banners", true)) {
            action(R.drawable.ic_image, R.string.set_background) {
                pickTarget = item.id to true
                pickImage.launch("image/*")
            }
        }
        if (prefs.getBoolean("show_icons", true)) {
            action(R.drawable.ic_account_circle, R.string.set_icon) {
                pickTarget = item.id to false
                pickImage.launch("image/*")
            }
        }
        if (prefs.getBoolean("show_banners", true) && customFile("banner_${item.id}.png").exists()) action(R.drawable.ic_delete, R.string.remove_background) {
            customFile("banner_${item.id}.png").delete()
            refresh()
        }
        if (prefs.getBoolean("show_icons", true) && customFile("icon_${item.id}.png").exists()) action(R.drawable.ic_delete, R.string.remove_icon) {
            customFile("icon_${item.id}.png").delete()
            refresh()
        }
        action(R.drawable.ic_add, R.string.add_to_home) { addToHomeScreen(item) }
        action(
            if (item.hidden) R.drawable.ic_visibility else R.drawable.ic_visibility_off,
            if (item.hidden) R.string.unhide else R.string.hide
        ) { toggleHidden(item) }
        action(
            R.drawable.ic_terminal,
            if (item.id in getConsoleModules()) R.string.console_disable else R.string.console_enable
        ) { toggleConsole(item) }

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

    data class Module(val name: String, val id: String, val desc: String, val author: String, val version: String, val banner: Bitmap? = null, val icon: Bitmap? = null, val size: String = "", val date: String = "", val disabled: Boolean = false, var pinned: Boolean = false, var hidden: Boolean = false)

    class ViewHolder(val binding: ItemModuleBinding) : RecyclerView.ViewHolder(binding.root)

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
            holder.binding.size.text = item.size
            holder.binding.size.isVisible = item.size.isNotEmpty()
            if (item.disabled || item.hidden) {
                holder.binding.date.text = listOfNotNull(
                    if (item.disabled) getString(R.string.module_disabled) else null,
                    if (item.hidden) getString(R.string.module_hidden) else null
                ).joinToString(" · ")
                holder.binding.date.setTextColor(DISABLED_RED)
                holder.binding.date.isVisible = true
            } else {
                holder.binding.date.text = item.date
                holder.binding.date.setTextColor(
                    MaterialColors.getColor(holder.binding.date, com.google.android.material.R.attr.colorOnSurfaceVariant)
                )
                holder.binding.date.isVisible = item.date.isNotEmpty()
            }
            holder.binding.desc.text = item.desc
            holder.binding.banner.setImageBitmap(item.banner)
            holder.binding.banner.isVisible = item.banner != null
            val iconsOn = prefs.getBoolean("show_icons", true)
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
