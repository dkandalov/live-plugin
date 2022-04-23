package liveplugin.implementation.toolwindow.popup

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileChooser.actions.NewFolderAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import liveplugin.implementation.common.Icons
import liveplugin.implementation.common.IdeUtil
import liveplugin.implementation.LivePluginPaths
import liveplugin.implementation.toolwindow.addplugin.*
import liveplugin.implementation.pluginrunner.groovy.GroovyPluginRunner
import liveplugin.implementation.pluginrunner.kotlin.KotlinPluginRunner
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
