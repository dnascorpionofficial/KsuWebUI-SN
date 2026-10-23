package io.github.a13e300.ksuwebui

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatDelegate
import com.topjohnwu.superuser.Shell
import java.util.concurrent.Executors

class App : Application() {
    companion object {
        lateinit var instance: App
            private set
        val executor by lazy { Executors.newCachedThreadPool() }
        val handler = Handler(Looper.getMainLooper())
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        val dark = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("dark_mode", true)
        AppCompatDelegate.setDefaultNightMode(
            if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
        Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER))
    }
}
