package liveplugin.toolwindow.addplugin

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import liveplugin.IdeUtil
import liveplugin.Icons
import liveplugin.LivePluginAppComponent.Companion.groovyExamplesPath
import liveplugin.LivePluginAppComponent.Companion.kotlinExamplesPath
import liveplugin.LivePluginAppComponent.Companion.livepluginsPath
import liveplugin.LivePluginAppComponent.Companion.pluginExists
import liveplugin.LivePluginAppComponent.Companion.readSampleScriptFile
import liveplugin.pluginrunner.GroovyPluginRunner
import liveplugin.pluginrunner.kotlin.KotlinPluginRunner
import liveplugin.toolwindow.RefreshPluginsPanelAction
import liveplugin.toolwindow.util.PluginsIO
import java.io.IOException

class AddNewGroovyPluginAction: AddNewPluginAction(
    "Groovy Plugin",
    "Create new Groovy plugin",
    GroovyPluginRunner.mainScript,
    readSampleScriptFile(groovyExamplesPath, "default-plugin.groovy")
)

class AddNewKotlinPluginAction: AddNewPluginAction(
    "Kotlin Plugin",
    "Create new Kotlin plugin",
    KotlinPluginRunner.mainScript,
    readSampleScriptFile(kotlinExamplesPath, "default-plugin.kts")
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
            event.project,
            "Enter new plugin name:",
            addNewPluginTitle, null, "", PluginIdValidator()
        ) ?: return

        try {

            PluginsIO.createFile(livepluginsPath + "/" + newPluginId, scriptFileName, scriptFileText)

        } catch (e: IOException) {
            if (project != null) {
                IdeUtil.showErrorDialog(project, "Error adding plugin '$newPluginId' to $livepluginsPath", addNewPluginTitle)
            }
            log.error(e)
        }
        RefreshPluginsPanelAction.refreshPluginTree()
    }
}

class PluginIdValidator: InputValidatorEx {
    private var errorText: String? = null

    override fun checkInput(pluginId: String): Boolean {
        val isValid = !pluginExists(pluginId)
        errorText = if (isValid) null else "There is already a plugin with this name"
        return isValid
    }

    override fun getErrorText(pluginId: String) = errorText

    override fun canClose(pluginId: String) = true
}
