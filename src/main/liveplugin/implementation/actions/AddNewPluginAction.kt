package liveplugin.implementation.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import liveplugin.implementation.LivePlugin.Companion.livePluginsById
import liveplugin.implementation.LivePluginPaths.groovyExamplesPath
import liveplugin.implementation.LivePluginPaths.kotlinExamplesPath
import liveplugin.implementation.LivePluginPaths.livePluginsPath
import liveplugin.implementation.common.Icons.newPluginIcon
import liveplugin.implementation.common.IdeUtil.showError
import liveplugin.implementation.common.IdeUtil.showInputDialog
import liveplugin.implementation.common.createFile
import liveplugin.implementation.common.inputValidator
import liveplugin.implementation.pluginrunner.groovy.GroovyPluginRunner.Companion.groovyScriptFile
import liveplugin.implementation.pluginrunner.kotlin.KotlinPluginRunner.Companion.kotlinScriptFile
import liveplugin.implementation.readSampleScriptFile
import java.io.IOException

class AddNewGroovyPluginAction : AddNewPluginAction(
    text = "Groovy Plugin",
    description = "Create new Groovy plugin",
    scriptFileName = groovyScriptFile,
    scriptFileText = readSampleScriptFile("$groovyExamplesPath/default-plugin.groovy")
)

class AddNewKotlinPluginAction : AddNewPluginAction(
    text = "Kotlin Plugin",
    description = "Create new Kotlin plugin",
    scriptFileName = kotlinScriptFile,
    scriptFileText = readSampleScriptFile("$kotlinExamplesPath/default-plugin.kts")
)

open class AddNewPluginAction(
    private val text: String,
    description: String,
    private val scriptFileName: String,
    private val scriptFileText: String
) : AnAction(text, description, newPluginIcon), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        val newPluginId = project.showInputDialog(message = "Enter new plugin name:", "Add $text", isNewPluginNameValidator) ?: return
        try {
            createFile("$livePluginsPath/$newPluginId", scriptFileName, scriptFileText, whenCreated = { virtualFile ->
                if (project != null) FileEditorManager.getInstance(project).openFile(virtualFile, true)
            })
        } catch (e: IOException) {
            project.showError("Error adding plugin \"$newPluginId\": ${e.message}", e)
        }
    }
}

val isNewPluginNameValidator = inputValidator { pluginId ->
    if (pluginId in livePluginsById().keys) "There is already a plugin with this name" else null
}
