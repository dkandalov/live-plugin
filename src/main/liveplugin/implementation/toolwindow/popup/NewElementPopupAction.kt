package liveplugin.implementation.toolwindow.popup

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileChooser.actions.NewFolderAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import liveplugin.implementation.LivePluginPaths.groovyExamplesPath
import liveplugin.implementation.LivePluginPaths.kotlinExamplesPath
import liveplugin.implementation.common.Icons.newFolderIcon
import liveplugin.implementation.common.IdeUtil.groovyFileType
import liveplugin.implementation.common.IdeUtil.kotlinFileType
import liveplugin.implementation.common.IdeUtil.textFileType
import liveplugin.implementation.common.IdeUtil.xmlFileType
import liveplugin.implementation.pluginrunner.groovy.GroovyPluginRunner
import liveplugin.implementation.pluginrunner.kotlin.KotlinPluginRunner
import liveplugin.implementation.toolwindow.addplugin.*
import liveplugin.implementation.toolwindow.util.readSampleScriptFile

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
                    AddPluginFromGistDelegateAction(),
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
        GroovyPluginRunner.mainScript,
        GroovyPluginRunner.mainScript,
        readSampleScriptFile("$groovyExamplesPath/default-plugin.groovy"),
        groovyFileType,
    )

    private class NewKotlinMainScript: NewFileFromTemplateAction(
        KotlinPluginRunner.mainScript,
        KotlinPluginRunner.mainScript,
        readSampleScriptFile("$kotlinExamplesPath/default-plugin.kts"),
        kotlinFileType
    )

    private class NewGroovyTestScript: NewFileFromTemplateAction(
        GroovyPluginRunner.testScript,
        GroovyPluginRunner.testScript,
        readSampleScriptFile("$groovyExamplesPath/default-plugin-test.groovy"),
        groovyFileType
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
