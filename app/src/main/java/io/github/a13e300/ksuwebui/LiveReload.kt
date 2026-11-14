package io.github.a13e300.ksuwebui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.topjohnwu.superuser.ipc.RootService
import io.github.a13e300.ksuwebui.services.FileWatchService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

private const val DEBOUNCE_MS = 300L

class LiveReload(private val context: Context, private val webRoot: File) {

    val enabled = MutableStateFlow(false)
    private val changes = Channel<String>(Channel.UNLIMITED)
    private val listener = object : IFileChangeListener.Stub() {
        override fun onChanged(path: String) {
            changes.trySend(path)
        }
    }

    suspend fun watch() {
        enabled.collectLatest { on ->
            if (!on) return@collectLatest
            while (changes.tryReceive().isSuccess) {
            }
            var connection: ServiceConnection? = null
            var watcher: IFileWatcher? = null
            try {
                val w = suspendCancellableCoroutine<IFileWatcher> { cont ->
                    val conn = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                            if (cont.isActive) cont.resume(IFileWatcher.Stub.asInterface(binder))
                        }

                        override fun onServiceDisconnected(name: ComponentName?) {}
                    }
                    connection = conn
                    RootService.bind(Intent(context, FileWatchService::class.java), conn)
                }
                watcher = w
                withContext(Dispatchers.IO) { w.start(webRoot.path, listener) }
                awaitCancellation()
            } finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    try {
                        watcher?.stop(listener)
                    } catch (_: Exception) {
                    }
                }
                connection?.let { runCatching { RootService.unbind(it) } }
            }
        }
    }

    suspend fun reloadOnChange(swapStyles: suspend (List<String>) -> Boolean, reload: () -> Unit) {
        while (true) {
            val paths = linkedSetOf(changes.receive())
            delay(DEBOUNCE_MS)
            while (true) {
                paths.add(changes.tryReceive().getOrNull() ?: break)
            }
            val onlyStyles = paths.all { it.endsWith(".css", ignoreCase = true) }
            if (onlyStyles && swapStyles(paths.toList())) continue
            reload()
        }
    }
}
