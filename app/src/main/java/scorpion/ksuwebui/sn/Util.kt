package scorpion.ksuwebui.sn

import com.topjohnwu.superuser.Shell

inline fun <T> withNewRootShell(block: Shell.() -> T): T = createRootShell().use(block)

fun createRootShell(): Shell =
    Shell.Builder.create().setFlags(Shell.FLAG_MOUNT_MASTER).build()
