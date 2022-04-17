package liveplugin.toolwindow.addplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import liveplugin.Icons
import liveplugin.IdeUtil
import liveplugin.LivePluginAppComponent.Companion.pluginIdToPathMap
import liveplugin.LivePluginPaths
import liveplugin.LivePluginPaths.groovyExamplesPath
import liveplugin.LivePluginPaths.kotlinExamplesPath
import liveplugin.pluginrunner.groovy.GroovyPluginRunner
import liveplugin.pluginrunner.kotlin.KotlinPluginRunner
import liveplugin.toolwindow.RefreshPluginsPanelAction
import liveplugin.toolwindow.util.createFile
import liveplugin.toolwindow.util.readSampleScriptFile
import java.io.IOException

class AddNewGroovyPluginAction: AddNewPluginAction(
    text = "Groovy Plugin",
    description = "Create new Groovy plugin",
    scriptFileName = GroovyPluginRunner.mainScript,
    scriptFileText = readSampleScriptFile("$groovyExamplesPath/default-plugin.groovy")
)

class AddNewKotlinPluginAction: AddNewPluginAction(
    text = "Kotlin Plugin",
    description = "Create new Kotlin plugin",
    scriptFileName = KotlinPluginRunner.mainScript,
    scriptFileText = readSampleScriptFile("$kotlinExamplesPath/default-plugin.kts")
)

open class AddNewPluginAction(
    text: String,
    description: String,
    private val scriptFileName: String,
    private val scriptFileText: String
): AnAction(text, description, Icons.newPluginIcon), DumbAware {

    private val log = Logger.getInstance(AddNewPluginAction::class.java)
    private val addNewPluginTitle = "Add $text"

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project

        val newPluginId = Messages.showInputDialog(
            project,
            "Enter new plugin name:",
            addNewPluginTitle, null, "", PluginIdValidator()
        ) ?: return

        try {
            createFile("${LivePluginPaths.livePluginsPath}/$newPluginId", scriptFileName, scriptFileText, whenCreated = { virtualFile ->
                if (project != null) FileEditorManager.getInstance(project).openFile(virtualFile, true)
            })
        } catch (e: IOException) {
            if (project != null) IdeUtil.showErrorDialog(project, "Error adding plugin '$newPluginId' to ${LivePluginPaths.livePluginsPath}", addNewPluginTitle)
            log.error(e)
        }
        RefreshPluginsPanelAction.refreshPluginTree()
    }
}

class PluginIdValidator: InputValidatorEx {
    private var errorText: String? = null

    override fun checkInput(pluginId: String) =
        if (pluginId in pluginIdToPathMap().keys) {
            errorText = "There is already a plugin with this name"
            false
        } else {
            errorText = null
            true
        }

    override fun getErrorText(pluginId: String) = errorText

    override fun canClose(pluginId: String) = true
}
