package scorpion.ksuwebui.sn

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.topjohnwu.superuser.nio.FileSystemManager
import scorpion.ksuwebui.sn.databinding.ActivityFileBrowserBinding
import scorpion.ksuwebui.sn.databinding.ItemFileBinding
import java.io.File

private class Entry(val name: String, val path: String, val directory: Boolean, val size: Long)

private class FileAdapter(private val onClick: (Entry) -> Unit) : RecyclerView.Adapter<FileAdapter.Holder>() {
    private var items: List<Entry> = emptyList()

    class Holder(val binding: ItemFileBinding) : RecyclerView.ViewHolder(binding.root)

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<Entry>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = items[position]
        holder.binding.icon.setImageResource(if (entry.directory) R.drawable.ic_folder else R.drawable.ic_file)
        holder.binding.name.text = entry.name
        holder.binding.meta.isVisible = !entry.directory
        if (!entry.directory) {
            holder.binding.meta.text = Formatter.formatShortFileSize(holder.itemView.context, entry.size)
        }
        holder.itemView.setOnClickListener { onClick(entry) }
    }
}

class FileBrowserActivity : AppCompatActivity(), FileSystemService.Listener {
    private lateinit var binding: ActivityFileBrowserBinding
    private lateinit var rootPath: String
    private lateinit var currentPath: String
    private lateinit var moduleName: String
    private var fs: FileSystemManager? = null
    private val adapter = FileAdapter { entry -> open(entry) }
    private val upCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            goUp()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)
        binding = ActivityFileBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        if (id.isEmpty() || id.contains('/') || id.contains("..")) {
            finish()
            return
        }
        moduleName = intent.getStringExtra(EXTRA_NAME) ?: id
        rootPath = "/data/adb/modules/$id/webroot"
        currentPath = rootPath

        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        binding.toolbar.setNavigationContentDescription(R.string.navigate_back)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.list.adapter = adapter
        onBackPressedDispatcher.addCallback(this, upCallback)
        updateTitle()
        binding.info.setText(R.string.loading)
        binding.info.isVisible = true
        FileSystemService.start(this)
    }

    override fun onResume() {
        super.onResume()
        if (fs != null) load(currentPath)
    }

    override fun onDestroy() {
        FileSystemService.removeListener(this)
        super.onDestroy()
    }

    override fun onServiceAvailable(fs: FileSystemManager) {
        this.fs = fs
        load(currentPath)
    }

    override fun onLaunchFailed() {
        binding.info.setText(R.string.please_grant_root)
        binding.info.isVisible = true
    }

    private fun updateTitle() {
        binding.toolbar.title = if (currentPath == rootPath) moduleName else currentPath.removePrefix("$rootPath/")
        upCallback.isEnabled = currentPath != rootPath
    }

    private fun goUp() {
        val parent = File(currentPath).parent ?: return
        if (!parent.startsWith(rootPath)) return
        currentPath = parent
        updateTitle()
        load(currentPath)
    }

    private fun open(entry: Entry) {
        if (entry.directory) {
            currentPath = entry.path
            updateTitle()
            load(currentPath)
        } else {
            startActivity(
                Intent(this, EditorActivity::class.java)
                    .putExtra(EditorActivity.EXTRA_PATH, entry.path)
                    .putExtra(EditorActivity.EXTRA_NAME, entry.name)
            )
        }
    }

    private fun load(path: String) {
        val manager = fs ?: return
        App.executor.submit {
            val entries = try {
                manager.getFile(path).listFiles().orEmpty().map { file ->
                    val directory = file.isDirectory
                    Entry(file.name, file.path, directory, if (directory) 0L else file.length())
                }.sortedWith(compareByDescending<Entry> { it.directory }.thenBy { it.name.lowercase() })
            } catch (e: Exception) {
                null
            }
            runOnUiThread {
                if (isDestroyed || path != currentPath) return@runOnUiThread
                if (entries == null) {
                    adapter.submit(emptyList())
                    binding.info.setText(R.string.file_open_failed)
                    binding.info.isVisible = true
                } else {
                    adapter.submit(entries)
                    binding.info.setText(R.string.folder_empty)
                    binding.info.isVisible = entries.isEmpty()
                }
            }
        }
    }

    companion object {
        const val EXTRA_ID = "module_id"
        const val EXTRA_NAME = "module_name"
    }
}
