package liveplugin

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
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

@Deprecated(message = "Replace with runOnEdtWithWriteLock", replaceWith = ReplaceWith("runOnEdtWithWriteLock"))
inline fun <T> withWriteLockOnEdt(crossinline f: () -> T): T =
    runOnEdtWithWriteLock(f)
