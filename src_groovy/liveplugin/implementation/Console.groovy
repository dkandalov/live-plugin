package liveplugin.implementation

import com.intellij.execution.ExecutionManager
import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.ConsoleInputFilterProvider
import com.intellij.execution.filters.InputFilter
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.actions.CloseAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import javax.swing.*
import java.awt.*
import java.util.List
import java.util.concurrent.atomic.AtomicReference

import static liveplugin.PluginUtil.invokeOnEDT

class Console {
	private static final extensionPoint = Extensions.rootArea.getExtensionPoint(ConsoleInputFilterProvider.INPUT_FILTER_PROVIDERS)

	static registerConsoleListener(String id, Closure callback) {
		GlobalVars.changeGlobalVar(id) { lastInputFilterProvider ->
			if (lastInputFilterProvider != null && extensionPoint.hasExtension(lastInputFilterProvider)) {
				extensionPoint.unregisterExtension(lastInputFilterProvider)
			}
			def notFilteringListener = new InputFilter() {
				@Override List<Pair<String, ConsoleViewContentType>> applyFilter(String consoleText, ConsoleViewContentType contentType) {
					callback(consoleText)
					null
				}
			}
		    def inputFilterProvider = new ConsoleInputFilterProvider() {
				@Override InputFilter[] getDefaultFilters(@NotNull Project project) {
					[notFilteringListener]
				}
			}
			extensionPoint.registerExtension(inputFilterProvider)
			inputFilterProvider
		}
	}

	static unregisterConsoleListener(String id) {
		def lastInputFilterProvider = GlobalVars.removeGlobalVar(id)
		if (lastInputFilterProvider != null && extensionPoint.hasExtension(lastInputFilterProvider)) {
			extensionPoint.unregisterExtension(lastInputFilterProvider)
		}
	}


	static ConsoleView showInConsole(@Nullable message, String consoleTitle = "", @NotNull Project project,
	                                 ConsoleViewContentType contentType = guessContentTypeOf(message)) {
		AtomicReference<ConsoleView> result = new AtomicReference(null)
		// Use reference for consoleTitle because get groovy Reference class like in this bug http://jira.codehaus.org/browse/GROOVY-5101
		AtomicReference<String> titleRef = new AtomicReference(consoleTitle)

		invokeOnEDT {
			ConsoleView console = TextConsoleBuilderFactory.instance.createBuilder(project).console
			console.print(Misc.asString(message), contentType)

			DefaultActionGroup toolbarActions = new DefaultActionGroup()
			def consoleComponent = new MyConsolePanel(console, toolbarActions)
			RunContentDescriptor descriptor = new RunContentDescriptor(console, null, consoleComponent, titleRef.get()) {
				@Override boolean isContentReuseProhibited() { true }
				@Override Icon getIcon() { AllIcons.Nodes.Plugin }
			}
			Executor executor = DefaultRunExecutor.runExecutorInstance

			toolbarActions.add(new CloseAction(executor, descriptor, project))
			console.createConsoleActions().each{ toolbarActions.add(it) }

			ExecutionManager.getInstance(project).contentManager.showRunContent(executor, descriptor)
			result.set(console)
		}
		result.get()
	}

	static ConsoleViewContentType guessContentTypeOf(text) {
		text instanceof Throwable ? ConsoleViewContentType.ERROR_OUTPUT : ConsoleViewContentType.NORMAL_OUTPUT
	}

	private static class MyConsolePanel extends JPanel {
		MyConsolePanel(ExecutionConsole consoleView, ActionGroup toolbarActions) {
			super(new BorderLayout())
			def toolbarPanel = new JPanel(new BorderLayout())
			toolbarPanel.add(ActionManager.instance.createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions, false).component)
			add(toolbarPanel, BorderLayout.WEST)
			add(consoleView.component, BorderLayout.CENTER)
		}
	}

}
