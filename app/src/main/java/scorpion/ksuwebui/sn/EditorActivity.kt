package scorpion.ksuwebui.sn

import android.content.res.Configuration
import android.graphics.Color
import android.view.inputmethod.EditorInfo
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.topjohnwu.superuser.nio.FileSystemManager
import scorpion.ksuwebui.sn.databinding.ActivityEditorBinding
import scorpion.ksuwebui.sn.databinding.DialogUnsavedBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val MAX_BYTES = 1_000_000L
private const val WINDOW_MARGIN = 20_000
private const val HIGHLIGHT_DELAY_MS = 350L
private const val WINDOW_DELAY_MS = 80L
private const val SEARCH_DELAY_MS = 150L
private const val MAX_MATCHES = 3000
private const val MAX_HISTORY = 300
private const val MERGE_LIMIT = 200
private const val MAX_HISTORY_CHARS = 3_000_000L
private const val LARGE_TEXT = 300_000
private const val LARGE_HIGHLIGHT_DELAY_MS = 900L

private class SyntaxSpan(color: Int) : ForegroundColorSpan(color)

private class MatchSpan(color: Int) : BackgroundColorSpan(color)

private class CurrentMatchSpan(color: Int) : BackgroundColorSpan(color)

private class Edit(var start: Int, var removed: String, var added: String)

private sealed class LoadResult {
    class Loaded(val text: String) : LoadResult()
    data object TooLarge : LoadResult()
    data object NotText : LoadResult()
    data object Failed : LoadResult()
}

class EditorActivity : AppCompatActivity(), FileSystemService.Listener {
    private lateinit var binding: ActivityEditorBinding
    private lateinit var path: String
    private var fs: FileSystemManager? = null
    private var language = Language.PLAIN
    private var crlf = false
    private var loaded = false
    private var loading = false
    private var saving = false
    private var dirty = false
    private var settingText = false
    private var revision = 0
    private var highlightJob: Job? = null
    private var windowJob: Job? = null
    private var tokens: List<Token> = emptyList()
    private var tokensRevision = -1
    private var appliedStart = 0
    private var appliedEnd = 0
    private var applyingHistory = false
    private var pendingRemoved = ""
    private val undoStack = ArrayList<Edit>()
    private val redoStack = ArrayList<Edit>()
    private var searchJob: Job? = null
    private var matches: List<Int> = emptyList()
    private var currentMatch = -1
    private var matchLength = 0
    private var currentSpan: CurrentMatchSpan? = null
    private var savedEdit: Edit? = null
    private var savedSignature = 0L
    private val searchCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            closeSearch()
        }
    }
    private val leaveCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            confirmLeave()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }

        path = intent.getStringExtra(EXTRA_PATH).orEmpty()
        if (!isAllowedPath(path)) {
            finish()
            return
        }
        val name = intent.getStringExtra(EXTRA_NAME) ?: File(path).name
        language = Language.forName(name)

        binding.toolbar.title = name
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        binding.toolbar.setNavigationContentDescription(R.string.navigate_back)
        binding.toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        binding.toolbar.inflateMenu(R.menu.editor_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.save -> save(false)
                R.id.undo -> undo()
                R.id.redo -> redo()
                R.id.search -> openSearch()
            }
            true
        }
        binding.scroll.setOnScrollChangeListener(
            NestedScrollView.OnScrollChangeListener { _, _, _, _, _ -> scheduleWindow() }
        )
        binding.editor.isVisible = false
        binding.editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                pendingRemoved = if (s != null && !settingText && !applyingHistory) {
                    s.subSequence(start, start + count).toString()
                } else {
                    ""
                }
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s != null && !settingText && !applyingHistory) {
                    record(start, pendingRemoved, s.subSequence(start, start + count).toString())
                }
            }

            override fun afterTextChanged(s: Editable?) {
                onEdited()
            }
        })
        binding.searchInput.doAfterTextChanged { scheduleSearch() }
        binding.searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                goToMatch(1)
                true
            } else {
                false
            }
        }
        binding.searchNext.setOnClickListener { goToMatch(1) }
        binding.searchPrevious.setOnClickListener { goToMatch(-1) }
        binding.searchClose.setOnClickListener { closeSearch() }
        onBackPressedDispatcher.addCallback(this, leaveCallback)
        onBackPressedDispatcher.addCallback(this, searchCallback)
        binding.info.setText(R.string.loading)
        binding.info.isVisible = true
        updateSaveState()
        FileSystemService.start(this)
    }

    override fun onDestroy() {
        FileSystemService.removeListener(this)
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        scheduleHighlight(0L)
    }

    override fun onServiceAvailable(fs: FileSystemManager) {
        this.fs = fs
        if (loaded || loading) return
        loading = true
        App.executor.submit {
            val result = try {
                read(fs)
            } catch (e: Exception) {
                LoadResult.Failed
            }
            runOnUiThread { if (!isDestroyed) show(result) }
        }
    }

    override fun onLaunchFailed() {
        binding.info.setText(R.string.please_grant_root)
        binding.info.isVisible = true
    }

    private fun isAllowedPath(value: String): Boolean =
        value.startsWith("/data/adb/modules/") && value.contains("/webroot/") &&
            !value.contains("/../") && !value.endsWith("/..")

    private fun read(manager: FileSystemManager): LoadResult {
        val file = manager.getFile(path)
        if (!file.isFile) return LoadResult.Failed
        if (file.length() > MAX_BYTES) return LoadResult.TooLarge
        val bytes = file.newInputStream().use { it.readBytes() }
        val head = minOf(bytes.size, 8192)
        for (index in 0 until head) {
            if (bytes[index] == 0.toByte()) return LoadResult.NotText
        }
        val text = String(bytes, Charsets.UTF_8)
        if (!text.toByteArray(Charsets.UTF_8).contentEquals(bytes)) return LoadResult.NotText
        return LoadResult.Loaded(text)
    }

    private fun show(result: LoadResult) {
        loading = false
        when (result) {
            is LoadResult.Loaded -> {
                crlf = result.text.contains("\r\n")
                val text = if (crlf) result.text.replace("\r\n", "\n") else result.text
                settingText = true
                binding.editor.setText(text)
                settingText = false
                undoStack.clear()
                redoStack.clear()
                savedEdit = null
                savedSignature = 0L
                loaded = true
                dirty = false
                binding.info.isVisible = false
                binding.editor.isVisible = true
                updateSaveState()
                scheduleHighlight(0L)
            }
            LoadResult.TooLarge -> showError(R.string.file_too_large)
            LoadResult.NotText -> showError(R.string.file_not_text)
            LoadResult.Failed -> showError(R.string.file_open_failed)
        }
    }

    private fun showError(message: Int) {
        binding.editor.isVisible = false
        binding.info.setText(message)
        binding.info.isVisible = true
    }

    private fun onEdited() {
        if (settingText) return
        revision++
        if (!dirty) {
            dirty = true
            updateSaveState()
        }
        scheduleHighlight(if (binding.editor.text.length > LARGE_TEXT) LARGE_HIGHLIGHT_DELAY_MS else HIGHLIGHT_DELAY_MS)
        if (binding.searchBar.isVisible) scheduleSearch()
    }

    private fun record(start: Int, removed: String, added: String) {
        if (removed.isEmpty() && added.isEmpty()) return
        redoStack.clear()
        val last = undoStack.lastOrNull()
        if (last != null && last.removed.isEmpty() && removed.isEmpty() &&
            start == last.start + last.added.length && last.added.length < MERGE_LIMIT && !added.contains('\n')
        ) {
            last.added += added
        } else if (last != null && last.added.isEmpty() && added.isEmpty() &&
            start + removed.length == last.start && last.removed.length < MERGE_LIMIT
        ) {
            last.start = start
            last.removed = removed + last.removed
        } else {
            undoStack.add(Edit(start, removed, added))
            if (undoStack.size > MAX_HISTORY) undoStack.removeAt(0)
        }
        var total = undoStack.sumOf { it.removed.length.toLong() + it.added.length }
        while (total > MAX_HISTORY_CHARS && undoStack.size > 1) {
            val dropped = undoStack.removeAt(0)
            total -= dropped.removed.length.toLong() + dropped.added.length
        }
        updateSaveState()
    }

    private fun signature(edit: Edit?): Long =
        if (edit == null) 0L else edit.start * 1_000_003L + edit.removed.length * 1009L + edit.added.length

    private fun isClean(): Boolean = undoStack.lastOrNull() === savedEdit && signature(savedEdit) == savedSignature

    private fun applyEdit(from: Int, length: Int, text: String) {
        val editable = binding.editor.text
        val start = from.coerceIn(0, editable.length)
        val end = (from + length).coerceIn(start, editable.length)
        applyingHistory = true
        editable.replace(start, end, text)
        applyingHistory = false
        binding.editor.setSelection((start + text.length).coerceIn(0, binding.editor.text.length))
    }

    private fun undo() {
        val edit = undoStack.removeLastOrNull() ?: return
        applyEdit(edit.start, edit.added.length, edit.removed)
        redoStack.add(edit)
        dirty = !isClean()
        updateSaveState()
    }

    private fun redo() {
        val edit = redoStack.removeLastOrNull() ?: return
        applyEdit(edit.start, edit.removed.length, edit.added)
        undoStack.add(edit)
        dirty = !isClean()
        updateSaveState()
    }

    private fun updateSaveState() {
        setActionEnabled(R.id.save, dirty && loaded && !saving)
        setActionEnabled(R.id.undo, loaded && undoStack.isNotEmpty())
        setActionEnabled(R.id.redo, loaded && redoStack.isNotEmpty())
        setActionEnabled(R.id.search, loaded)
        leaveCallback.isEnabled = dirty
    }

    private fun setActionEnabled(id: Int, enabled: Boolean) {
        val item = binding.toolbar.menu.findItem(id) ?: return
        item.isEnabled = enabled
        item.icon?.mutate()?.alpha = if (enabled) 255 else 90
    }

    private fun openSearch() {
        if (!loaded) return
        val editor = binding.editor
        val selected = if (editor.selectionStart >= 0 && editor.selectionEnd > editor.selectionStart) {
            editor.text.substring(editor.selectionStart, editor.selectionEnd)
        } else {
            ""
        }
        binding.searchBar.isVisible = true
        searchCallback.isEnabled = true
        if (selected.isNotEmpty() && !selected.contains('\n')) binding.searchInput.setText(selected)
        binding.searchInput.requestFocus()
        binding.searchInput.setSelection(binding.searchInput.text.length)
        WindowCompat.getInsetsController(window, binding.searchInput).show(WindowInsetsCompat.Type.ime())
        scheduleSearch()
    }

    private fun closeSearch() {
        searchJob?.cancel()
        clearMatchSpans(binding.editor.text)
        matches = emptyList()
        currentMatch = -1
        binding.searchBar.isVisible = false
        searchCallback.isEnabled = false
        WindowCompat.getInsetsController(window, binding.searchInput).hide(WindowInsetsCompat.Type.ime())
    }

    private fun scheduleSearch() {
        searchJob?.cancel()
        searchJob = lifecycleScope.launch {
            delay(SEARCH_DELAY_MS)
            runSearch()
        }
    }

    private fun runSearch() {
        val editable = binding.editor.text
        clearMatchSpans(editable)
        val query = binding.searchInput.text.toString()
        val found = ArrayList<Int>()
        if (query.isNotEmpty()) {
            val text = editable.toString()
            var index = text.indexOf(query, 0, ignoreCase = true)
            while (index >= 0 && found.size < MAX_MATCHES) {
                found.add(index)
                index = text.indexOf(query, index + query.length, ignoreCase = true)
            }
        }
        matches = found
        matchLength = query.length
        val cursor = maxOf(binding.editor.selectionStart, 0)
        currentMatch = if (found.isEmpty()) -1 else found.indexOfFirst { it >= cursor }.let { if (it < 0) 0 else it }
        paintAll()
        if (currentMatch >= 0) scrollToMatch()
    }

    private fun goToMatch(delta: Int) {
        if (matches.isEmpty()) return
        currentMatch = (currentMatch + delta + matches.size) % matches.size
        paintCurrent()
        scrollToMatch()
    }

    private fun paintAll() {
        val editable = binding.editor.text
        clearMatchSpans(editable)
        val color = Color.parseColor("#66FFD54F")
        for (start in matches) {
            val end = start + matchLength
            if (end > editable.length) continue
            editable.setSpan(MatchSpan(color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        paintCurrent()
    }

    private fun paintCurrent() {
        val editable = binding.editor.text
        currentSpan?.let { editable.removeSpan(it) }
        currentSpan = null
        val start = matches.getOrNull(currentMatch)
        if (start != null && start + matchLength <= editable.length) {
            val span = CurrentMatchSpan(Color.parseColor("#CCFF9800"))
            editable.setSpan(span, start, start + matchLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            currentSpan = span
        }
        binding.searchCount.text = when {
            binding.searchInput.text.isEmpty() -> ""
            matches.isEmpty() -> getString(R.string.no_results)
            matches.size >= MAX_MATCHES -> "${currentMatch + 1}/$MAX_MATCHES+"
            else -> "${currentMatch + 1}/${matches.size}"
        }
    }

    private fun scrollToMatch() {
        val layout = binding.editor.layout ?: return
        val start = matches.getOrNull(currentMatch) ?: return
        if (start > binding.editor.text.length) return
        val top = layout.getLineTop(layout.getLineForOffset(start)) + binding.editor.totalPaddingTop
        binding.scroll.smoothScrollTo(0, (top - binding.scroll.height / 3).coerceAtLeast(0))
    }

    private fun clearMatchSpans(editable: Editable) {
        currentSpan = null
        for (span in editable.getSpans(0, editable.length, MatchSpan::class.java)) editable.removeSpan(span)
        for (span in editable.getSpans(0, editable.length, CurrentMatchSpan::class.java)) editable.removeSpan(span)
    }

    private fun isDark(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun colorFor(kind: TokenKind, dark: Boolean): Int = when (kind) {
        TokenKind.COMMENT -> Color.parseColor(if (dark) "#7F848E" else "#6A737D")
        TokenKind.STRING -> Color.parseColor(if (dark) "#98C379" else "#0B7A3E")
        TokenKind.KEYWORD -> Color.parseColor(if (dark) "#C678DD" else "#A626A4")
        TokenKind.NUMBER -> Color.parseColor(if (dark) "#56B6C2" else "#1F6FEB")
        TokenKind.TAG -> Color.parseColor(if (dark) "#E06C75" else "#C7254E")
        TokenKind.ATTRIBUTE -> Color.parseColor(if (dark) "#D19A66" else "#986801")
        TokenKind.PROPERTY -> Color.parseColor(if (dark) "#61AFEF" else "#0550AE")
    }

    private fun scheduleHighlight(delayMs: Long) {
        highlightJob?.cancel()
        if (language == Language.PLAIN || !loaded) return
        highlightJob = lifecycleScope.launch {
            delay(delayMs)
            val editable = binding.editor.text
            val stamp = revision
            val snapshot = editable.toString()
            val found = withContext(Dispatchers.Default) {
                SyntaxHighlighter.tokenize(snapshot, language).sortedBy { it.start }
            }
            if (stamp != revision || editable.length != snapshot.length) return@launch
            tokens = found
            tokensRevision = stamp
            applyWindow(true)
        }
    }

    private fun scheduleWindow() {
        windowJob?.cancel()
        windowJob = lifecycleScope.launch {
            delay(WINDOW_DELAY_MS)
            applyWindow(false)
        }
    }

    private fun firstTokenIndex(from: Int): Int {
        var low = 0
        var high = tokens.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (tokens[mid].start < from) low = mid + 1 else high = mid
        }
        while (low > 0 && tokens[low - 1].end > from) low--
        return low
    }

    private fun applyWindow(force: Boolean) {
        if (!loaded || tokensRevision != revision) return
        val editable = binding.editor.text
        val layout = binding.editor.layout
        var from = 0
        var to = minOf(editable.length, WINDOW_MARGIN * 2)
        if (layout != null && layout.lineCount > 0) {
            val padding = binding.editor.totalPaddingTop
            val top = (binding.scroll.scrollY - padding).coerceAtLeast(0)
            val bottom = (binding.scroll.scrollY + binding.scroll.height - padding).coerceAtLeast(0)
            val visibleFrom = layout.getLineStart(layout.getLineForVertical(top))
            val visibleTo = layout.getLineEnd(layout.getLineForVertical(bottom))
            val needFrom = appliedStart > 0 && visibleFrom - appliedStart < WINDOW_MARGIN / 4
            val needTo = appliedEnd < editable.length && appliedEnd - visibleTo < WINDOW_MARGIN / 4
            if (!force && !needFrom && !needTo) return
            from = (visibleFrom - WINDOW_MARGIN).coerceAtLeast(0)
            to = (visibleTo + WINDOW_MARGIN).coerceAtMost(editable.length)
        }
        clearSpans(editable)
        val dark = isDark()
        var index = firstTokenIndex(from)
        while (index < tokens.size) {
            val token = tokens[index]
            if (token.start >= to) break
            if (token.end > from && token.start < token.end && token.end <= editable.length) {
                editable.setSpan(SyntaxSpan(colorFor(token.kind, dark)), token.start, token.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            index++
        }
        appliedStart = from
        appliedEnd = to
    }

    private fun clearSpans(editable: Editable) {
        for (span in editable.getSpans(0, editable.length, SyntaxSpan::class.java)) {
            editable.removeSpan(span)
        }
    }

    private fun save(finishAfter: Boolean) {
        val manager = fs ?: return
        if (!loaded || saving) return
        val text = binding.editor.text.toString()
        val data = (if (crlf) text.replace("\n", "\r\n") else text).toByteArray(Charsets.UTF_8)
        val stamp = revision
        val markerEdit = undoStack.lastOrNull()
        val markerSignature = signature(markerEdit)
        saving = true
        updateSaveState()
        App.executor.submit {
            val ok = try {
                manager.getFile(path).newOutputStream(false).use { it.write(data) }
                true
            } catch (e: Exception) {
                false
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                saving = false
                if (ok) {
                    savedEdit = markerEdit
                    savedSignature = markerSignature
                    if (stamp == revision) dirty = false
                    Toast.makeText(this, R.string.file_saved, Toast.LENGTH_SHORT).show()
                    updateSaveState()
                    if (finishAfter) finish()
                } else {
                    Toast.makeText(this, R.string.file_save_failed, Toast.LENGTH_LONG).show()
                    updateSaveState()
                }
            }
        }
    }

    private fun confirmLeave() {
        val view = DialogUnsavedBinding.inflate(layoutInflater)
        view.title.setText(R.string.unsaved_title)
        view.message.setText(R.string.unsaved_message)
        val dialog = MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_KsuWebUISN_ButtonDialog)
            .setView(view.root)
            .create()
        view.cancel.setOnClickListener { dialog.dismiss() }
        view.discard.setOnClickListener {
            dialog.dismiss()
            dirty = false
            leaveCallback.isEnabled = false
            finish()
        }
        view.save.setOnClickListener {
            dialog.dismiss()
            save(true)
        }
        dialog.showBlurred(this)
    }

    companion object {
        const val EXTRA_PATH = "file_path"
        const val EXTRA_NAME = "file_name"
    }
}
