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
import android.graphics.Shader
import android.os.Build
import android.os.SystemClock
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.PopupMenu
import android.widget.Toast
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.topjohnwu.superuser.nio.FileSystemManager
import io.github.a13e300.ksuwebui.databinding.ActivityMainBinding
import io.github.a13e300.ksuwebui.databinding.ItemModuleBinding
import androidx.core.net.toUri
import androidx.core.content.edit
import com.topjohnwu.superuser.Shell
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("NotifyDataSetChanged")
class MainActivity : AppCompatActivity(), FileSystemService.Listener {
    private lateinit var binding: ActivityMainBinding
    private var allModules = emptyList<Module>()
    private var moduleList = emptyList<Module>()
    private var searchQuery = ""
    private var imeWasVisible = false
    private var imeHiddenAt = 0L

    // while searching: first back hides the keyboard, the next back closes the search
    private val searchBack = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            val visible = ViewCompat.getRootWindowInsets(binding.root)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
            // the keyboard may already have been closed by this same back press
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
    private lateinit var adapter: Adapter
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private var shouldRefresh = false
    private var pickTarget: Pair<String, Boolean>? = null // module id, true = background / false = icon
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

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable edge to edge
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // plain toolbar, no ActionBar: AppCompat's ActionBar collapses an expanded search on back
        binding.toolbar.setTitle(R.string.app_name)
        binding.toolbar.inflateMenu(R.menu.main_menu)
        onBackPressedDispatcher.addCallback(this, searchBack)

        lifecycleScope.launch(Dispatchers.IO) {
            AppList.getApps(this@MainActivity)
        }

        // remember when the keyboard closes
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val visible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (imeWasVisible && !visible) imeHiddenAt = SystemClock.uptimeMillis()
            imeWasVisible = visible
            ViewCompat.onApplyWindowInsets(v, insets)
        }

        // Add insets
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

        setupMenu(binding.toolbar.menu)

        // update check: once per app open, never in the background
        if (prefs.getBoolean("update_check", true)) {
            if (savedInstanceState == null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val update = UpdateChecker.check()
                    UpdateChecker.available = update
                    withContext(Dispatchers.Main) {
                        showUpdate(update)
                        // with Debugging on, say what the check found (or why it found nothing)
                        if (prefs.getBoolean("enable_web_debugging", BuildConfig.DEBUG)) {
                            Toast.makeText(this@MainActivity, UpdateChecker.status, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } else {
                // recreated (theme switch, rotation): reuse the last result instead of checking again
                showUpdate(UpdateChecker.available)
            }
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
        menu.findItem(R.id.update).setOnMenuItemClickListener {
            UpdateChecker.available?.let { showUpdateDialog(it) }
            true
        }
        menu.findItem(R.id.update_check).apply {
            isChecked = prefs.getBoolean("update_check", true)
            setOnMenuItemClickListener {
                val newValue = !it.isChecked
                prefs.edit { putBoolean("update_check", newValue) }
                it.isChecked = newValue
                if (!newValue) {
                    UpdateChecker.available = null
                    showUpdate(null)
                }
                true
            }
        }
        menu.findItem(R.id.enable_webview_debugging).apply {
            isChecked = prefs.getBoolean("enable_web_debugging", BuildConfig.DEBUG)
            setOnMenuItemClickListener {
                val newValue = !it.isChecked
                prefs.edit { putBoolean("enable_web_debugging", newValue) }
                it.isChecked = newValue
                true
            }
        }
        menu.findItem(R.id.show_disabled).apply {
            isChecked = prefs.getBoolean("show_disabled", false)
            setOnMenuItemClickListener {
                val newValue = !it.isChecked
                prefs.edit { putBoolean("show_disabled", newValue) }
                it.isChecked = newValue
                refresh()
                true
            }
        }
        menu.findItem(R.id.dark_mode).apply {
            isChecked = prefs.getBoolean("dark_mode", true)
            setOnMenuItemClickListener {
                val newValue = !it.isChecked
                prefs.edit { putBoolean("dark_mode", newValue) }
                it.isChecked = newValue
                AppCompatDelegate.setDefaultNightMode(
                    if (newValue) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                )
                true
            }
        }
    }

    private fun showUpdate(update: UpdateChecker.Update?) {
        binding.toolbar.menu.findItem(R.id.update)?.isVisible = update != null
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
            val showDisabled = prefs.getBoolean("show_disabled", false)
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
                val banner = loadBanner(fs, f.path, bannerRaw ?: coverRaw, id)
                val icon = loadIcon(fs, f.path, id)
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
                mods.add(Module(name, id, desc, author, version, banner, icon, size, date))
            }
            val pinnedIds = getPinnedModules()
            mods.forEach { it.pinned = it.id in pinnedIds }
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

    // GPU-backed bitmap, uploaded here on the worker thread instead of on first draw while scrolling
    private fun toGpu(bmp: Bitmap): Bitmap =
        bmp.copy(Bitmap.Config.HARDWARE, false)?.also { bmp.recycle() } ?: bmp

    private fun customFile(name: String) = File(File(filesDir, "custom"), name)

    private fun saveCustom(name: String, bmp: Bitmap) {
        val f = customFile(name)
        f.parentFile?.mkdirs()
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // custom background > module.prop "banner" / "cover" (relative path, absolute path or <MODULECWD>/path, no http URLs)
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

    // custom icon > webroot/icon.png > webroot/icon.jpg
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

    // Crops to 12:5 and fades the lower part out into the card colour (no darkening, so no black band)
    private fun blendBanner(src: Bitmap): Bitmap {
        var cw = src.width
        var ch = (cw / 2.4f).toInt()
        if (ch > src.height) {
            ch = src.height
            cw = (ch * 2.4f).toInt()
        }
        // never keep more pixels than the screen shows: oversized textures make fast scrolling stutter
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
        // fade into the card from 75% down to the bottom (keeps artwork text near the bottom readable)
        c.drawRect(0f, 0f, wf, hf, Paint().apply {
            shader = LinearGradient(0f, hf * 0.75f, 0f, hf, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        })
        return toGpu(out)
    }

    // pinned first, then modules with a background, then by name
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

    // letters and digits only, so "remalwack" matches "Re-Malwack"
    private fun normalize(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    private fun applyFilter() {
        val q = normalize(searchQuery)
        moduleList = (if (q.isEmpty()) allModules else allModules.filter {
            normalize(it.name).contains(q)
        }).sortedWith(moduleOrder)
        adapter.notifyDataSetChanged()
        if (allModules.isNotEmpty()) {
            binding.info.setText(R.string.no_results)
            binding.info.isVisible = moduleList.isEmpty()
        }
    }

    private fun showModuleSettings(anchor: View, item: Module) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, if (item.pinned) R.string.unpin else R.string.pin)
        popup.menu.add(0, 2, 1, R.string.set_background)
        popup.menu.add(0, 3, 2, R.string.set_icon)
        if (customFile("banner_${item.id}.png").exists()) popup.menu.add(0, 4, 3, R.string.remove_background)
        if (customFile("icon_${item.id}.png").exists()) popup.menu.add(0, 5, 4, R.string.remove_icon)
        popup.setOnMenuItemClickListener { m ->
            when (m.itemId) {
                1 -> togglePin(item)
                2 -> {
                    pickTarget = item.id to true
                    pickImage.launch("image/*")
                }
                3 -> {
                    pickTarget = item.id to false
                    pickImage.launch("image/*")
                }
                4 -> {
                    customFile("banner_${item.id}.png").delete()
                    refresh()
                }
                5 -> {
                    customFile("icon_${item.id}.png").delete()
                    refresh()
                }
            }
            true
        }
        popup.show()
    }

    override fun onLaunchFailed() {
        allModules = emptyList()
        moduleList = emptyList()
        adapter.notifyDataSetChanged()
        binding.info.setText(R.string.please_grant_root)
        binding.info.isVisible = true
        binding.swipeRefresh.isRefreshing = false
    }

    data class Module(val name: String, val id: String, val desc: String, val author: String, val version: String, val banner: Bitmap? = null, val icon: Bitmap? = null, val size: String = "", val date: String = "", var pinned: Boolean = false)

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
            holder.binding.date.text = item.date
            holder.binding.date.isVisible = item.date.isNotEmpty()
            holder.binding.desc.text = item.desc
            holder.binding.banner.setImageBitmap(item.banner)
            holder.binding.banner.isVisible = item.banner != null
            holder.binding.icon.setImageBitmap(item.icon)
            holder.binding.icon.isVisible = item.icon != null
            holder.binding.settings.setOnClickListener { showModuleSettings(it, item) }
            holder.binding.name.setCompoundDrawablesRelativeWithIntrinsicBounds(
                0, 0, if (item.pinned) R.drawable.ic_push_pin else 0, 0
            )
            holder.binding.root.setOnClickListener {
                shouldRefresh = true
                startActivity(
                    Intent(this@MainActivity, WebUIActivity::class.java)
                        .setData("ksuwebui://webui/$id".toUri())
                        .putExtra("id", id)
                        .putExtra("name", name)
                )
            }
        }

    }

    override fun onDestroy() {
        super.onDestroy()
        FileSystemService.removeListener(this)
    }
}
