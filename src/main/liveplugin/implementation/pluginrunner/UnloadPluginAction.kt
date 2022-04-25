package liveplugin.implementation.pluginrunner

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import liveplugin.implementation.LivePlugin
import liveplugin.implementation.common.Icons
import liveplugin.implementation.livePlugins
import liveplugin.implementation.pluginrunner.RunPluginAction.Companion.pluginNameInActionText

class UnloadPluginAction: AnAction("Unload Plugin", "Unload selected plugins", Icons.unloadPluginIcon), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        unloadPlugins(event.livePlugins())
    }

    override fun update(event: AnActionEvent) {
        val livePlugins = event.livePlugins().filter { it.canBeUnloaded() }
        event.presentation.isEnabled = livePlugins.isNotEmpty()
        if (event.presentation.isEnabled) {
            event.presentation.setText("Unload ${pluginNameInActionText(livePlugins)}", false)
        }
    }

    companion object {
        @JvmStatic fun unloadPlugins(livePlugins: Collection<LivePlugin>) {
            livePlugins.forEach { Binding.lookup(it)?.dispose() }
        }
    }
}

fun LivePlugin.canBeUnloaded() = Binding.lookup(this) != null
