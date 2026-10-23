package io.github.a13e300.ksuwebui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.widget.PopupMenu
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    private var moduleList = emptyList<Module>()
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
                val i = moduleList.indexOfFirst { it.id == id }
                if (i >= 0) {
                    val m = moduleList[i]
                    moduleList = moduleList.toMutableList().also {
                        it[i] = if (isBanner) m.copy(banner = shown) else m.copy(icon = shown)
                    }
                    adapter.notifyItemChanged(i)
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
        setSupportActionBar(binding.toolbar)

        lifecycleScope.launch(Dispatchers.IO) {
            AppList.getApps(this@MainActivity)
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

        adapter = Adapter()
        binding.list.adapter = adapter
        binding.swipeRefresh.setOnRefreshListener {
            refresh()
        }
        refresh()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
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
        return true
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
                val meta = listOfNotNull(
                    sizes[id]?.let { formatSize(it) },
                    if (modified > 0) SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(modified)) else null
                ).joinToString("  •  ")
                mods.add(Module(name, id, desc, author, version, banner, icon, meta))
            }
            val pinnedIds = getPinnedModules()
            mods.forEach { it.pinned = it.id in pinnedIds }
            mods.sortWith(compareByDescending<Module> { it.pinned }.thenBy { it.name.lowercase() })
            runOnUiThread {
                moduleList = mods
                adapter.notifyDataSetChanged()
                binding.swipeRefresh.isRefreshing = false
                if (mods.isEmpty()) {
                    binding.info.setText(R.string.no_modules)
                    binding.info.isVisible = true
                } else {
                    binding.info.isVisible = false
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
        return out
    }

    // Crops to 12:5, darkens the lower part (image stays sharp) and fades it out into the card colour
    private fun blendBanner(src: Bitmap): Bitmap {
        var cw = src.width
        var ch = (cw / 2.4f).toInt()
        if (ch > src.height) {
            ch = src.height
            cw = (ch * 2.4f).toInt()
        }
        val cropped = Bitmap.createBitmap(src, (src.width - cw) / 2, (src.height - ch) / 2, cw, ch)
        val wf = cw.toFloat()
        val hf = ch.toFloat()

        val out = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(cropped, 0f, 0f, null)
        // reduce brightness from 45% down to the bottom
        c.drawRect(0f, 0f, wf, hf, Paint().apply {
            shader = LinearGradient(0f, hf * 0.45f, 0f, hf, Color.TRANSPARENT, 0xCC000000.toInt(), Shader.TileMode.CLAMP)
        })
        // fade into the card from 55% down to the bottom
        c.drawRect(0f, 0f, wf, hf, Paint().apply {
            shader = LinearGradient(0f, hf * 0.55f, 0f, hf, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        })
        return out
    }

    private fun togglePin(item: Module) {
        item.pinned = !item.pinned
        val pinnedIds = getPinnedModules()
        if (item.pinned) {
            pinnedIds.add(item.id)
        } else {
            pinnedIds.remove(item.id)
        }
        savePinnedModules(pinnedIds)
        moduleList = moduleList.sortedWith(compareByDescending<Module> { it.pinned }.thenBy { it.name.lowercase() })
        adapter.notifyDataSetChanged()
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
        moduleList = emptyList()
        adapter.notifyDataSetChanged()
        binding.info.setText(R.string.please_grant_root)
        binding.info.isVisible = true
        binding.swipeRefresh.isRefreshing = false
    }

    data class Module(val name: String, val id: String, val desc: String, val author: String, val version: String, val banner: Bitmap? = null, val icon: Bitmap? = null, val meta: String = "", var pinned: Boolean = false)

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
            holder.binding.desc.text = item.desc
            holder.binding.banner.setImageBitmap(item.banner)
            holder.binding.banner.isVisible = item.banner != null
            holder.binding.icon.setImageBitmap(item.icon)
            holder.binding.icon.isVisible = item.icon != null
            holder.binding.meta.text = item.meta
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
