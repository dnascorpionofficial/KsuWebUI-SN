package io.github.a13e300.ksuwebui

import android.os.SystemClock
import android.webkit.JavascriptInterface
import org.json.JSONObject

private const val MAX_CALLS = 200
private const val MAX_CALL_CHARS = 5_000_000
private const val MAX_TOTAL_CHARS = 30_000_000

class ExecLog(private val enabled: Boolean) {

    private class Event(val seq: Int, val id: Int, val json: String)

    private val lock = Any()
    private val buffer = ArrayList<Event>()
    private val liveIds = ArrayDeque<Int>()
    private val started = HashMap<Int, Long>()
    private val used = HashMap<Int, Int>()
    private val capped = HashSet<Int>()
    private var total = 0
    private var seq = 0
    private var nextId = 1

    fun start(kind: String, cmd: String, options: String?): Int {
        if (!enabled) return 0
        return synchronized(lock) {
            val id = nextId++
            started[id] = SystemClock.elapsedRealtime()
            liveIds.addLast(id)
            while (liveIds.size > MAX_CALLS) drop(liveIds.removeFirst())
            add(
                id,
                JSONObject().put("t", "start").put("k", kind).put("c", cmd)
                    .put("o", options.orEmpty()).put("at", System.currentTimeMillis())
            )
            id
        }
    }

    fun out(id: Int, text: String) = data(id, "out", text)

    fun err(id: Int, text: String) = data(id, "err", text)

    fun end(id: Int, code: Int?) {
        if (!enabled) return
        synchronized(lock) {
            val t0 = started[id] ?: return
            add(
                id,
                JSONObject().put("t", "end").put("code", code ?: JSONObject.NULL)
                    .put("ms", SystemClock.elapsedRealtime() - t0)
            )
        }
    }

    fun events(after: Int): String = synchronized(lock) {
        var i = buffer.size
        while (i > 0 && buffer[i - 1].seq > after) i--
        buffer.subList(i, buffer.size).joinToString(",", "[", "]") { it.json }
    }

    fun clear() = synchronized(lock) {
        buffer.clear()
        liveIds.clear()
        started.clear()
        used.clear()
        capped.clear()
        total = 0
    }

    private fun data(id: Int, type: String, text: String) {
        if (!enabled || text.isEmpty()) return
        synchronized(lock) {
            if (id !in started || id in capped) return
            val room = MAX_CALL_CHARS - (used[id] ?: 0)
            val piece = if (text.length > room) text.substring(0, room) else text
            if (piece.isNotEmpty()) {
                used[id] = (used[id] ?: 0) + piece.length
                total += piece.length
                add(id, JSONObject().put("t", type).put("d", piece))
                while (total > MAX_TOTAL_CHARS && liveIds.size > 1) drop(liveIds.removeFirst())
                if (id !in started) return
            }
            if (piece.length < text.length) {
                capped.add(id)
                add(id, JSONObject().put("t", "trunc"))
            }
        }
    }

    private fun add(id: Int, obj: JSONObject) {
        seq++
        buffer.add(Event(seq, id, obj.put("id", id).put("s", seq).toString()))
    }

    private fun drop(id: Int) {
        buffer.removeAll { it.id == id }
        started.remove(id)
        capped.remove(id)
        total -= used.remove(id) ?: 0
    }
}

class ExecLogInterface(private val log: ExecLog) {

    @JavascriptInterface
    fun events(after: Int): String = log.events(after)

    @JavascriptInterface
    fun clear() {
        log.clear()
    }
}
