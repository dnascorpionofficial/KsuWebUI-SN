package io.github.a13e300.ksuwebui

import android.app.Application
import android.content.SharedPreferences
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
        AppCompatDelegate.setDefaultNightMode(nightMode(themeMode(getSharedPreferences("settings", MODE_PRIVATE))))
        Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER))
    }
}

const val THEME_SYSTEM = 0
const val THEME_DARK = 1
const val THEME_LIGHT = 2

fun themeMode(prefs: SharedPreferences): Int = when {
    prefs.contains("theme_mode") -> prefs.getInt("theme_mode", THEME_SYSTEM)
    prefs.contains("dark_mode") -> if (prefs.getBoolean("dark_mode", true)) THEME_DARK else THEME_LIGHT
    else -> THEME_SYSTEM
}

fun nightMode(mode: Int): Int = when (mode) {
    THEME_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
    else -> AppCompatDelegate.MODE_NIGHT_NO
}
