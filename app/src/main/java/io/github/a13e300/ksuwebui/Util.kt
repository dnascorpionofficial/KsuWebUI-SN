package io.github.a13e300.ksuwebui

import android.content.Context
import com.topjohnwu.superuser.Shell

inline fun <T> withNewRootShell(
    globalMnt: Boolean = false,
    block: Shell.() -> T
): T {
    return createRootShell(globalMnt).use(block)
}

fun createRootShell(globalMnt: Boolean = false): Shell {
    Shell.enableVerboseLogging = App.instance
        .getSharedPreferences("settings", Context.MODE_PRIVATE)
        .getBoolean("enable_web_debugging", BuildConfig.DEBUG)
    val builder = Shell.Builder.create()
    if (globalMnt) {
        builder.setFlags(Shell.FLAG_MOUNT_MASTER)
    }
    return builder.build()
}
