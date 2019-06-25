package liveplugin.pluginrunner

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import liveplugin.Icons
import liveplugin.IdeUtil
import liveplugin.pluginrunner.groovy.GroovyPluginRunner
import liveplugin.pluginrunner.groovy.GroovyPluginRunner.Companion.testScript

class RunPluginTestsAction: AnAction("Run Plugin Tests", "Run Plugin Integration Tests", Icons.testPluginIcon), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        IdeUtil.saveAllFiles()
        val errorReporter = ErrorReporter()
        val pluginRunners = listOf(GroovyPluginRunner(testScript, errorReporter))
        runPlugins(event.selectedFiles(), event, errorReporter, pluginRunners)
    }

    override fun update(event: AnActionEvent) {
        val errorReporter = ErrorReporter()
        val pluginRunners = listOf(GroovyPluginRunner(testScript, errorReporter))
        event.presentation.isEnabled = event.selectedFiles().canBeHandledBy(pluginRunners)
    }
}
