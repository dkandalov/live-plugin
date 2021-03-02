package liveplugin.actions

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import liveplugin.implementation.Actions
import liveplugin.noNeedForEdtOrWriteActionWhenUsingActionManager
import liveplugin.pluginrunner.kotlin.KotlinScriptTemplate
import java.util.function.Function

fun KotlinScriptTemplate.registerAction(
    id: String,
    keyStroke: String = "",
    actionGroupId: String? = null,
    displayText: String = id,
    action: AnAction
): AnAction = noNeedForEdtOrWriteActionWhenUsingActionManager {
    registerAction(id, keyStroke, actionGroupId, displayText, pluginDisposable, action)
}

fun KotlinScriptTemplate.registerAction(
    id: String,
    keyStroke: String = "",
    actionGroupId: String? = null,
    displayText: String = id,
    callback: (AnActionEvent) -> Unit
): AnAction = noNeedForEdtOrWriteActionWhenUsingActionManager {
    registerAction(id, keyStroke, actionGroupId, displayText, pluginDisposable, callback)
}

fun registerAction(
    id: String,
    keyStroke: String = "",
    actionGroupId: String? = null,
    displayText: String = id,
    disposable: Disposable,
    action: AnAction
): AnAction = noNeedForEdtOrWriteActionWhenUsingActionManager {
    Actions.registerAction(id, keyStroke, actionGroupId, displayText, disposable, action)
}

fun registerAction(
    id: String,
    keyStroke: String = "",
    actionGroupId: String? = null,
    displayText: String = id,
    disposable: Disposable,
    callback: (AnActionEvent) -> Unit
): AnAction = noNeedForEdtOrWriteActionWhenUsingActionManager {
    val function = Function<AnActionEvent, Unit> { callback(it) }
    return Actions.registerAction(id, keyStroke, actionGroupId, displayText, disposable, function)
}
