package spp.plugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.util.concurrent.atomic.AtomicBoolean

fun Disposable.whenDisposed(f: () -> Unit = {}): Disposable {
    Disposer.register(this) { f() }
    return this
}

fun newDisposable(debugName: String? = null, whenDisposed: () -> Unit = {}): Disposable =
    object : Disposable {
        override fun dispose() = whenDisposed()
        override fun toString(): String = debugName ?: super.toString()
    }

fun Disposable.registerParent(vararg parentDisposables: Disposable): Disposable {
    val isDisposed = AtomicBoolean(false)
    parentDisposables.forEach { parentDisposable ->
        Disposer.register(parentDisposable) {
            val wasUpdated = isDisposed.compareAndSet(false, true)
            if (wasUpdated) Disposer.dispose(this)
        }
    }
    return this
}
