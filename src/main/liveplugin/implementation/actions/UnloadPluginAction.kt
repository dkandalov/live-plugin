package liveplugin.implementation.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import liveplugin.implementation.LivePlugin
import liveplugin.implementation.actions.RunPluginAction.Companion.pluginNameInActionText
import liveplugin.implementation.common.Icons.unloadPluginIcon
import liveplugin.implementation.livePlugins
import liveplugin.implementation.pluginrunner.Binding
import liveplugin.implementation.pluginrunner.PluginRunner.Companion.unloadPlugins

class UnloadPluginAction: AnAction("Unload Plugin", "Unload selected plugins", unloadPluginIcon), DumbAware {
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

    override fun getActionUpdateThread() = BGT
}

fun LivePlugin.canBeUnloaded() = Binding.lookup(this) != null
