package liveplugin.toolwindow.popup

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileChooser.actions.NewFolderAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import liveplugin.Icons
import liveplugin.IdeUtil
import liveplugin.LivePluginPaths
import liveplugin.pluginrunner.groovy.GroovyPluginRunner
import liveplugin.pluginrunner.kotlin.KotlinPluginRunner
import liveplugin.toolwindow.addplugin.*
import liveplugin.toolwindow.util.readSampleScriptFile

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
                    NewGroovyFileAction(),
                    NewKotlinFileAction(),
                    NewTextFileAction(),
                    NewDirectoryAction(),
                    NewGroovyMainScript(),
                    NewKotlinMainScript(),
                    NewGroovyTestScript(),
                    NewPluginXmlScript(),
                    Separator.getInstance(),
                    AddNewGroovyPluginAction(),
                    AddNewKotlinPluginAction(),
                    AddPluginFromGistDelegateAction(),
                    AddPluginFromGitHubDelegateAction(),
                    AddGroovyExamplesActionGroup(),
                    AddKotlinExamplesActionGroup(),
                )
            ).also { it.isPopup = true }
        }
    }

    private class NewGroovyFileAction: NewFileAction("Groovy File", IdeUtil.groovyFileType)

    private class NewKotlinFileAction: NewFileAction("Kotlin File", IdeUtil.kotlinFileType)

    private class NewTextFileAction: NewFileAction("Text File", IdeUtil.textFileType)

    private class NewDirectoryAction: NewFolderAction("Directory", "", Icons.newFolderIcon)

    private class NewGroovyMainScript: NewFileFromTemplateAction(
        GroovyPluginRunner.mainScript,
        GroovyPluginRunner.mainScript,
        readSampleScriptFile("${LivePluginPaths.groovyExamplesPath}/default-plugin.groovy"),
        IdeUtil.groovyFileType
    )

    private class NewKotlinMainScript: NewFileFromTemplateAction(
        KotlinPluginRunner.mainScript,
        KotlinPluginRunner.mainScript,
        readSampleScriptFile("${LivePluginPaths.kotlinExamplesPath}/default-plugin.kts"),
        IdeUtil.kotlinFileType
    )

    private class NewGroovyTestScript: NewFileFromTemplateAction(
        GroovyPluginRunner.testScript,
        GroovyPluginRunner.testScript,
        readSampleScriptFile("${LivePluginPaths.groovyExamplesPath}/default-plugin-test.groovy"),
        IdeUtil.groovyFileType
    )
}

class NewPluginXmlScript(
    fileContent: String = readSampleScriptFile("${LivePluginPaths.kotlinExamplesPath}/plugin.xml")
): NewFileFromTemplateAction(
    "plugin.xml",
    "plugin.xml",
    fileContent,
    IdeUtil.xmlFileType
)
