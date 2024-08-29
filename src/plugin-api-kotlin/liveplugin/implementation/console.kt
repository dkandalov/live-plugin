package liveplugin.implementation

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.ConsoleInputFilterProvider
import com.intellij.execution.filters.ConsoleInputFilterProvider.INPUT_FILTER_PROVIDERS
import com.intellij.execution.filters.InputFilter
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.*
import com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import liveplugin.implementation.common.IdeUtil
import liveplugin.runOnEdt
import java.awt.BorderLayout
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JPanel

fun registerConsoleListener(disposable: Disposable, callback: (String) -> String?) {
    registerConsoleFilter(disposable) { consoleText ->
        callback(consoleText)
        null // no filtering of console output
    }
}

fun registerConsoleFilter(disposable: Disposable, callback: (String) -> String?) {
    registerConsoleFilter(disposable) { text, contentType ->
        val newConsoleText = callback(text)
        if (newConsoleText == null) null
        else listOf(Pair(newConsoleText, contentType))
    }
}

fun registerConsoleFilter(disposable: Disposable, inputFilter: InputFilter) {
    val extensionPoint = ApplicationManager.getApplication().extensionArea.getExtensionPoint(INPUT_FILTER_PROVIDERS)
    extensionPoint.registerExtension(consoleFilterProviderFor(inputFilter), LoadingOrder.FIRST, disposable)
}

private fun consoleFilterProviderFor(inputFilter: InputFilter) =
    ConsoleInputFilterProvider { arrayOf(inputFilter) }

fun showInConsole(
    message: String,
    consoleTitle: String = "",
    project: Project,
    contentType: ConsoleViewContentType = NORMAL_OUTPUT
): ConsoleView {
    val result = AtomicReference<ConsoleView>(null)
    val titleRef = AtomicReference(consoleTitle)

    runOnEdt {
        val consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
        consoleView.print(message, contentType)

        val toolbarActions = DefaultActionGroup()
        val consoleComponent = MyConsolePanel(consoleView, toolbarActions)
        val descriptor = object : RunContentDescriptor(consoleView, null, consoleComponent, titleRef.get()) {
            override fun isContentReuseProhibited() = true
            override fun getIcon() = AllIcons.Nodes.Plugin
        }
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        toolbarActions.add(com.intellij.execution.ui.actions.CloseAction(executor, descriptor, project))
        consoleView.createConsoleActions().forEach { toolbarActions.add(it) }

        RunContentManager.getInstance(project).showRunContent(executor, descriptor)
        result.set(consoleView)
    }
    return result.get()
}

private class MyConsolePanel(consoleView: ExecutionConsole, toolbarActions: ActionGroup) : JPanel(BorderLayout()) {
    init {
        val toolbarPanel = JPanel(BorderLayout())
        toolbarPanel.add(ActionManager.getInstance().createActionToolbar(IdeUtil.livePluginActionPlace, toolbarActions, false).component)
        add(toolbarPanel, BorderLayout.WEST)
        add(consoleView.component, BorderLayout.CENTER)
    }
}
