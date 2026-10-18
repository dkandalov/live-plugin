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

class SetIntellijSourceCodePathAction : AnAction(), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        val dialog = IntellijSourceCodePathDialog(event.project)
        if (dialog.showAndGet()) {
            Settings.instance.intellijSourceCodePath = dialog.path.trim()
        }
    }

    override fun update(event: AnActionEvent) {
        val path = Settings.instance.intellijSourceCodePath
        event.presentation.text = if (path.isBlank()) "Set IntelliJ Source Code Path…" else "IntelliJ Source Code Path: $path"
    }

    override fun getActionUpdateThread() = BGT

    private class IntellijSourceCodePathDialog(private val project: Project?) : DialogWrapper(project, true) {
        var path: String = Settings.instance.intellijSourceCodePath

        init {
            title = "IntelliJ Source Code Path"
            init()
        }

        override fun createCenterPanel() = panel {
            row("Path:") {
                textFieldWithBrowseButton(FileChooserDescriptorFactory.createSingleFolderDescriptor(), project)
                    .align(AlignX.FILL)
                    .applyToComponent { text = path }
                    .onChanged { path = it.text }
            }.rowComment("Local checkout of intellij-community source code, used to look up IntelliJ Platform APIs.")
        }
    }
}
