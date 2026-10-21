package liveplugin.implementation.actions.settings

import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import liveplugin.implementation.Settings
import liveplugin.implementation.actions.mcp.isValidDirectory

class SetIntellijSourceCodePathAction : AnAction(), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        val dialog = PathDialog(event.project, Settings.instance.intellijSourceCodePath)
        if (dialog.showAndGet()) {
            Settings.instance.intellijSourceCodePath = dialog.path
        }
    }

    override fun update(event: AnActionEvent) {
        event.presentation.text = "IntelliJ Source Code Path for MCP…"
    }

    override fun getActionUpdateThread() = BGT

    private class PathDialog(private val project: Project?, var path: String) : DialogWrapper(project, true) {
        init {
            title = "IntelliJ Source Code Path for MCP"
            init()
        }

        override fun createCenterPanel() = panel {
            row("Path:") {
                textFieldWithBrowseButton(FileChooserDescriptorFactory.createSingleFolderDescriptor(), project)
                    .align(AlignX.FILL)
                    .applyToComponent { text = path }
                    .onChanged { path = it.text.trim() }
                    .validationOnInput {
                        if (isValidDirectory(it.text.trim())) null
                        else warning("Path doesn't exist, but it will still be saved")
                    }
            }.rowComment(
                "Checkout of <a href=\"https://github.com/JetBrains/intellij-community\">intellij-community source code</a>. " +
                    "Used by MCP to look up IntelliJ Platform APIs."
            )
        }
    }
}
