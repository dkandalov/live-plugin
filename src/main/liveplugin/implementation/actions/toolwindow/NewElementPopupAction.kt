package liveplugin.implementation.actions.toolwindow

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileChooser.actions.NewFolderAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import liveplugin.implementation.LivePluginPaths.groovyExamplesPath
import liveplugin.implementation.LivePluginPaths.kotlinExamplesPath
import liveplugin.implementation.actions.AddGroovyExamplesActionGroup
import liveplugin.implementation.actions.AddKotlinExamplesActionGroup
import liveplugin.implementation.actions.AddNewGroovyPluginAction
import liveplugin.implementation.actions.AddNewKotlinPluginAction
import liveplugin.implementation.actions.gist.AddPluginFromGistAction
import liveplugin.implementation.actions.git.AddPluginFromGitHubDelegateAction
import liveplugin.implementation.common.Icons.newFolderIcon
import liveplugin.implementation.common.IdeUtil.groovyFileType
import liveplugin.implementation.common.IdeUtil.kotlinFileType
import liveplugin.implementation.common.IdeUtil.textFileType
import liveplugin.implementation.common.IdeUtil.xmlFileType
import liveplugin.implementation.pluginrunner.groovy.GroovyPluginRunner.Companion.groovyScriptFile
import liveplugin.implementation.pluginrunner.groovy.GroovyPluginRunner.Companion.groovyTestScriptFile
import liveplugin.implementation.pluginrunner.kotlin.KotlinPluginRunner.Companion.kotlinScriptFile
import liveplugin.implementation.readSampleScriptFile

class NewElementPopupAction: AnAction(), DumbAware, PopupAction {
    override fun actionPerformed(event: AnActionEvent) {
        createPopup(event.dataContext).showInBestPositionFor(event.dataContext)
    }

    private fun createPopup(dataContext: DataContext): ListPopup =
        JBPopupFactory.getInstance().createActionGroupPopup(
            IdeBundle.message("title.popup.new.element"),
            livePluginNewElementPopup,
            dataContext, false, true, false, null, -1,
            LangDataKeys.PRESELECT_NEW_ACTION_CONDITION.getData(dataContext)
        )

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = true
    }

    companion object {
        val livePluginNewElementPopup by lazy {
            DefaultActionGroup(
                { "New" },
                listOf(
                    NewKotlinFileAction(),
                    NewGroovyFileAction(),
                    NewTextFileAction(),
                    NewDirectoryAction(),
                    NewKotlinMainScript(),
                    NewGroovyMainScript(),
                    NewGroovyTestScript(),
                    NewPluginXmlScript(),
                    Separator.getInstance(),
                    AddNewKotlinPluginAction(),
                    AddNewGroovyPluginAction(),
                    AddPluginFromGistAction(),
                    AddPluginFromGitHubDelegateAction(),
                    AddKotlinExamplesActionGroup(),
                    AddGroovyExamplesActionGroup(),
                )
            ).also { it.isPopup = true }
        }
    }

    private class NewGroovyFileAction: NewFileAction("Groovy File", groovyFileType)

    private class NewKotlinFileAction: NewFileAction("Kotlin File", kotlinFileType)

    private class NewTextFileAction: NewFileAction("Text File", textFileType)

    private class NewDirectoryAction: NewFolderAction("Directory", "", newFolderIcon)

    private class NewGroovyMainScript: NewFileFromTemplateAction(
        text = groovyScriptFile,
        newFileName = groovyScriptFile,
        fileContent = readSampleScriptFile("$groovyExamplesPath/default-plugin.groovy"),
        fileType = groovyFileType,
    )

    private class NewKotlinMainScript: NewFileFromTemplateAction(
        text = kotlinScriptFile,
        newFileName = kotlinScriptFile,
        fileContent = readSampleScriptFile("$kotlinExamplesPath/default-plugin.kts"),
        fileType = kotlinFileType
    )

    private class NewGroovyTestScript: NewFileFromTemplateAction(
        text = groovyTestScriptFile,
        newFileName = groovyTestScriptFile,
        fileContent = readSampleScriptFile("$groovyExamplesPath/default-plugin-test.groovy"),
        fileType = groovyFileType
    )
}

class NewPluginXmlScript(
    fileContent: String = readSampleScriptFile("$kotlinExamplesPath/plugin.xml")
): NewFileFromTemplateAction(
    "plugin.xml",
    "plugin.xml",
    fileContent,
    xmlFileType
)
