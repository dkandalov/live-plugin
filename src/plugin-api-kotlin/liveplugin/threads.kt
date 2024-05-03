@file:Suppress("unused")

package liveplugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.Computable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference

/**
 * See also [com.intellij.openapi.application.runInEdt]
 */
inline fun <T> runOnEdt(crossinline f: () -> T): T {
    val result = AtomicReference<T>()
    ApplicationManager.getApplication().invokeAndWait {
        result.set(f())
    }
    return result.get()
}

/**
 * See also [com.intellij.openapi.application.invokeLater]
 */
inline fun runLaterOnEdt(crossinline f: () -> Any?) =
    ApplicationManager.getApplication().invokeLater { f() }

/**
 * See also [com.intellij.openapi.application.runReadAction] and [com.intellij.openapi.application.ReadAction]
 * which have more confusing names because this is not related to IDE actions, i.e. AnAction class.
 */
inline fun <T> runWithReadLock(crossinline f: () -> T): T =
    ApplicationManager.getApplication().runReadAction(Computable { f() })

/**
 * See also [com.intellij.openapi.application.runWriteAction],
 * [com.intellij.openapi.application.runWriteActionAndWait] and [com.intellij.openapi.application.WriteAction]
 * which have more confusing names because this is not related to IDE actions, i.e. AnAction class.
 */
inline fun <T> runOnEdtWithWriteLock(crossinline f: () -> T): T =
    runOnEdt {
        ApplicationManager.getApplication().runWriteAction(Computable { f() })
    }

/**
 * See also [com.intellij.openapi.progress.ProgressManager]
 */
fun <T> runBackgroundTask(
    taskTitle: String = "Background task", // Empty title is logged as a warning by IJ
    canBeCancelledInUI: Boolean = true,
    task: (ProgressIndicator) -> T,
): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    val result = AtomicReference<T>()
    runLaterOnEdt {
        object: Task.Backgroundable(null, taskTitle, canBeCancelledInUI, ALWAYS_BACKGROUND) {
            override fun run(indicator: ProgressIndicator) = result.set(task(indicator))

            // Invoked on EDT
            override fun onSuccess() {
                future.complete(result.get())
            }

            // Invoked on EDT
            override fun onThrowable(error: Throwable) {
                future.completeExceptionally(error)
            }

            override fun onCancel() {
                future.cancel(true)
            }
        }.queue()
    }
    return future
}