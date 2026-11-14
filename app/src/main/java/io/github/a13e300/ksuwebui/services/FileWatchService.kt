package io.github.a13e300.ksuwebui.services

import android.content.Intent
import android.os.FileObserver
import android.os.IBinder
import android.os.RemoteException
import com.topjohnwu.superuser.ipc.RootService
import io.github.a13e300.ksuwebui.IFileChangeListener
import io.github.a13e300.ksuwebui.IFileWatcher
import java.io.File

private const val MASK = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or FileObserver.CREATE
private const val MAX_DEPTH = 6
private const val MAX_DIRS = 100
private val WATCHED = setOf("html", "htm", "js", "mjs", "css")

class FileWatchService : RootService() {

    override fun onBind(intent: Intent): IBinder = Stub()

    private class Stub : IFileWatcher.Stub() {
        private val watches = HashMap<IBinder, List<FileObserver>>()

        @Suppress("DEPRECATION")
        override fun start(root: String, listener: IFileChangeListener) {
            stop(listener)
            val dirs = File(root).walkTopDown().maxDepth(MAX_DEPTH)
                .filter { it.isDirectory }.take(MAX_DIRS).toList()
            val rootDir = File(root)
            val observers = dirs.map { dir ->
                val prefix = dir.relativeTo(rootDir).path
                object : FileObserver(dir.path, MASK) {
                    override fun onEvent(event: Int, path: String?) {
                        if (path == null || path.substringAfterLast('.', "").lowercase() !in WATCHED) return
                        try {
                            listener.onChanged(if (prefix.isEmpty()) path else "$prefix/$path")
                        } catch (_: RemoteException) {
                            stop(listener)
                        }
                    }
                }
            }
            synchronized(watches) { watches[listener.asBinder()] = observers }
            observers.forEach { it.startWatching() }
        }

        override fun stop(listener: IFileChangeListener) {
            val observers = synchronized(watches) { watches.remove(listener.asBinder()) } ?: return
            observers.forEach { it.stopWatching() }
        }
    }
}
