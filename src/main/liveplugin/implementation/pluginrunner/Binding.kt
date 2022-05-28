package liveplugin.implementation.pluginrunner

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import liveplugin.implementation.LivePlugin
import liveplugin.implementation.common.IdeUtil
import liveplugin.implementation.common.toFilePath
import liveplugin.implementation.pluginrunner.AnError.RunningError

class Binding(
    val project: Project?,
    val isIdeStartup: Boolean,
    val pluginPath: String,
    val pluginDisposable: Disposable
) {
    fun dispose() {
        Disposer.dispose(pluginDisposable)
        bindingByPluginId.remove(LivePlugin(pluginPath.toFilePath()).id)
    }

    companion object {
        private val bindingByPluginId = HashMap<String, Binding>()

        fun lookup(livePlugin: LivePlugin): Binding? =
            bindingByPluginId[livePlugin.id]

        fun create(livePlugin: LivePlugin, event: AnActionEvent): Binding {
            val oldBinding = bindingByPluginId[livePlugin.id]
            if (oldBinding != null) {
                try {
                    Disposer.dispose(oldBinding.pluginDisposable)
                } catch (e: Exception) {
                    displayError(livePlugin.id, RunningError(e), event.project)
                }
            }

            val disposable = object: Disposable {
                override fun dispose() {}
                override fun toString() = "LivePlugin: $livePlugin"
            }
            Disposer.register(ApplicationManager.getApplication(), disposable)

            val binding = Binding(event.project, event.place == IdeUtil.ideStartupActionPlace, livePlugin.path.value, disposable)
            bindingByPluginId[livePlugin.id] = binding

            return binding
        }
    }
}