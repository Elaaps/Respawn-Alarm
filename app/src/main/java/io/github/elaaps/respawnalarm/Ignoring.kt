package io.github.elaaps.respawnalarm

inline fun <reified E : Throwable> ignoring(block: () -> Unit) {
    try {
        block()
    } catch (e: Throwable) {
        if (e !is E) throw e
    }
}
