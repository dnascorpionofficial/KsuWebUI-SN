package io.github.a13e300.ksuwebui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.IBinder
import com.topjohnwu.superuser.ipc.RootService
import io.github.a13e300.ksuwebui.services.RootServices
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class AppInfo(val packageInfo: PackageInfo, val label: String) {
    val packageName: String get() = packageInfo.packageName
}

object AppList {
    private var cachedApps: List<AppInfo>? = null

    fun getApplist(): List<AppInfo> {
        return cachedApps ?: emptyList()
    }

    suspend fun getApps(context: Context): List<AppInfo> {
        cachedApps?.let { return it }
        val binder = bindRootService(context) ?: return emptyList()
        try {
            val rootService = IKsuWebuiStandaloneInterface.Stub.asInterface(binder)
            val slice = rootService.getPackages(PackageManager.GET_META_DATA)
            val packages = slice.list
            val pm = context.packageManager
            val apps = packages.map { pkg ->
                val label = pm.getApplicationLabel(pkg.applicationInfo!!).toString()
                AppInfo(pkg, label)
            }
            cachedApps = apps
            return apps
        } catch (_: Exception) {
            return emptyList()
        } finally {
            App.handler.post { stopRootService(context) }
        }
    }

    private suspend fun bindRootService(context: Context): IBinder? = suspendCoroutine { continuation ->
        var connected = false

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                connected = true
                continuation.resume(binder)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (!connected) continuation.resume(null)
            }
        }

        App.handler.post {
            RootService.bind(Intent(context, RootServices::class.java), connection)
        }
    }

    private fun stopRootService(context: Context) {
        val intent = Intent(context, RootServices::class.java)
        RootService.stop(intent)
    }
}
