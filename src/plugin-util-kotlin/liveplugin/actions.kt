@file:Suppress("unused")

package liveplugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.PlatformDataKeys.CONTEXT_COMPONENT
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid
import com.intellij.openapi.ui.popup.JBPopupFactory.ActionSelectionAid.*
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.NlsActions.ActionText
import com.intellij.openapi.wm.WindowManager
import liveplugin.pluginrunner.kotlin.LivePluginScript
import javax.swing.KeyStroke

/**
 * Registers action with the specified [id] (which must be unique in IDE)
 * where the action is represented as a [function] that takes an [AnActionEvent] and creates a side effect.
 *
 * You can specify [keyStroke] for the action. For example, "ctrl shift H",
 * or for a shortcut with double keystroke "alt C, alt D" (hold alt, press C, release C, press D).
 * Modification keys are lowercase, e.g. ctrl, alt, shift, cmd. Letters are uppercase.
 * Other keys are uppercase based on the constant names in [java.awt.event.KeyEvent] without "VK_" prefix,
 * e.g. ENTER, ESCAPE, SPACE, LEFT, UP, F1, F12. For details, see [javax.swing.KeyStroke.getKeyStroke] javadoc.
 *
 * You can also specify [actionGroupId] to add actions to existing menus,
 * e.g. "ToolsMenu" corresponds to `Main menu - Tools`. See [ActionGroupIds] for common group ids.
 *
 * Note that the action is registered with the `pluginDisposable` and will be automatically unregistered
 * when the plugin is unloaded or evaluated again. See https://plugins.jetbrains.com/docs/intellij/disposers.html.
 */
fun LivePluginScript.registerAction(
    @ActionText id: String,
    keyStroke: String? = null,
    actionGroupId: String? = null,
    positionInGroup: Constraints = Constraints.LAST,
    function: (AnActionEvent) -> Unit
): AnAction = noNeedForEdtOrWriteActionWhenUsingActionManager {
    registerAction(id, keyStroke?.toKeyboardShortcut(), actionGroupId, positionInGroup, pluginDisposable, AnAction(id, function))
}

fun LivePluginScript.registerAction(
    @ActionText id: String,
    keyStroke: String? = null,
    actionGroupId: String? = null,
    positionInGroup: Constraints = Constraints.LAST,
    action: AnAction
): AnAction = noNeedForEdtOrWriteActionWhenUsingActionManager {
    registerAction(id, keyStroke?.toKeyboardShortcut(), actionGroupId, positionInGroup, pluginDisposable, action)
}

fun registerAction(
    @ActionText id: String,
    keyStroke: String? = null,
    actionGroupId: String? = null,
    positionInGroup: Constraints = Constraints.LAST,
    disposable: Disposable,
    function: (AnActionEvent) -> Unit
): AnAction = noNeedForEdtOrWriteActionWhenUsingActionManager {
    registerAction(id, keyStroke?.toKeyboardShortcut(), actionGroupId, positionInGroup, disposable, AnAction(id, function))
}

fun registerAction(
    @ActionText id: String,
    keyStroke: String? = null,
    actionGroupId: String? = null,
    positionInGroup: Constraints = Constraints.LAST,
    disposable: Disposable,
    action: AnAction
): AnAction = noNeedForEdtOrWriteActionWhenUsingActionManager {
    registerAction(id, keyStroke?.toKeyboardShortcut(), actionGroupId, positionInGroup, disposable, action)
}

fun registerAction(
    @ActionText id: String,
    keyboardShortcut: KeyboardShortcut? = null,
    actionGroupId: String? = null,
    positionInGroup: Constraints = Constraints.LAST,
    disposable: Disposable,
    action: AnAction
): AnAction = noNeedForEdtOrWriteActionWhenUsingActionManager {
    val actionManager = ActionManager.getInstance()
    val keymapManager = KeymapManager.getInstance()

    if ((actionManager.getAction(id) != null)) error("Action '$id' is already registered")
    action.templatePresentation.setText(id, true)

    val actionGroup =
        if (actionGroupId == null) null
        else (actionManager.getAction(actionGroupId) as? DefaultActionGroup) ?: error("Action group id is not found: '$actionGroupId'")

    if (keyboardShortcut != null) keymapManager.activeKeymap.addShortcut(id, keyboardShortcut)
    actionManager.registerAction(id, action)
    actionGroup?.add(action, positionInGroup, actionManager)

    disposable.whenDisposed {
        actionGroup?.remove(action)
        actionManager.unregisterAction(id)
        if (keyboardShortcut != null) keymapManager.activeKeymap.removeShortcut(id, keyboardShortcut)
    }
    return action
}

fun AnAction(@ActionText text: String? = null, f: (AnActionEvent) -> Unit) =
    object: AnAction(text) {
        override fun actionPerformed(event: AnActionEvent) = f(event)
        override fun isDumbAware() = true
    }

fun PopupActionGroup(@ActionText name: String, vararg actions: AnAction) =
    DefaultActionGroup(name, actions.toList()).also { it.isPopup = true }

fun ActionGroup.createPopup(
    dataContext: DataContext? = null,
    selectionAidMethod: ActionSelectionAid = SPEEDSEARCH,
    showNumbers: Boolean = selectionAidMethod == NUMBERING || selectionAidMethod == ALPHA_NUMBERING,
    showDisabledActions: Boolean = false,
    isPreselected: (AnAction) -> Boolean = { false }
): ListPopup =
    JBPopupFactory.getInstance().createActionGroupPopup(
        templatePresentation.text,
        this,
        dataContext ?: defaultDataContext(), // Use default context so that IJ doesn't throw an exception.
        showNumbers,
        showDisabledActions,
        selectionAidMethod == MNEMONICS,
        null,
        -1,
        isPreselected
    )

private fun defaultDataContext() =
    MapDataContext(mapOf(CONTEXT_COMPONENT.name to WindowManager.getInstance().findVisibleFrame()))

private fun String.toKeyboardShortcut(): KeyboardShortcut? {
    val parts = split(",").map { it.trim() }.filter { it.isNotEmpty() }
        .map {
            it.replace(".", "PERIOD")
                .replace("-", "MINUS")
                .replace("=", "EQUALS")
                .replace(";", "SEMICOLON")
                .replace("/", "SLASH")
                .replace("[", "OPEN_BRACKET")
                .replace("\\", "BACK_SLASH")
                .replace("]", "CLOSE_BRACKET")
                .replace("`", "BACK_QUOTE")
                .replace("'", "QUOTE")
        }
    if (parts.isEmpty()) return null

    val firstKeystroke = KeyStroke.getKeyStroke(parts[0]) ?: error("Invalid keystroke '${this}'")
    val secondKeystroke = if (parts.size > 1) KeyStroke.getKeyStroke(parts[1]) else null
    return KeyboardShortcut(firstKeystroke, secondKeystroke)
}

@Suppress("unused")
object ActionGroupIds {
    // Action groups in the IDE main menu
    object Menu {
        val File = "FileMenu"
        val Edit = "EditMenu"
        val View = "ViewMenu"
        val Navigate = "GoToMenu"
        val Code = "CodeMenu"
        val Refactor = "RefactoringMenu"
        val Build = "BuildMenu"
        val Run = "RunMenu"
        val Tools = "ToolsMenu"
        val Vcs = "VcsGroups"
        val Window = "WindowMenu"
        val Help = "HelpMenu"
    }

    // Action groups in the toolbar (to enable check "Main Menu -> View -> Appearance -> Toolbar")
    object Toolbar {
        val Main = "MainToolBar"
        val Run = "ToolbarRunGroup"
        val Make = "ToolbarMakeGroup"
        val Find = "ToolbarFindGroup"
    }

    val NewFileMenu = "NewGroup"
    val EditorPopupMenu = "EditorPopupMenu"
    val GeneratePopupMenu = "GenerateGroup"
}

/**
 * @see liveplugin.PluginUtil.assertNoNeedForEdtOrWriteActionWhenUsingActionManager
 */
inline fun <T> noNeedForEdtOrWriteActionWhenUsingActionManager(f: () -> T) = f()