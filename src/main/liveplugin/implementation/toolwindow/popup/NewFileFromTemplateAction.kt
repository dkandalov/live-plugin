package liveplugin.implementation.toolwindow.popup

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileChooser.FileSystemTree
import com.intellij.openapi.fileChooser.actions.FileChooserAction
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.UIBundle
import liveplugin.implementation.LivePluginAppComponent
import liveplugin.implementation.LivePluginAppComponent.Companion.findParentPluginFolder
import liveplugin.implementation.common.toFilePath
import java.io.IOException

open class NewFileFromTemplateAction(
    text: String,
    private val newFileName: String,
    private val fileContent: String,
    private val fileType: FileType
): FileChooserAction(text, text, null /*infer icon from file type*/) {

    override fun update(fileSystemTree: FileSystemTree, event: AnActionEvent) {
        val parentFile = fileSystemTree.newFileParent?.directory()

        val enabled = parentFile != null &&
            parentFile.findChild(newFileName) == null &&
            parentFile.toFilePath().findParentPluginFolder() in LivePluginAppComponent.pluginIdToPathMap().values

        event.presentation.apply {
            isEnabled = enabled
            isVisible = enabled
            icon = fileType.icon
        }
    }

    override fun actionPerformed(fileSystemTree: FileSystemTree, event: AnActionEvent) {
        if (fileSystemTree.createNewFile(event.project)) {
            val message = UIBundle.message("create.new.file.could.not.create.file.error.message", newFileName)
            Messages.showMessageDialog(message, UIBundle.message("error.dialog.title"), Messages.getErrorIcon())
        }
    }

    private fun VirtualFile.directory() = if (isDirectory) this else parent

    private fun FileSystemTree.createNewFile(project: Project?): Boolean {
        val parentDirectory = newFileParent?.directory() ?: return false
        val file = createNewFile(project, parentDirectory) ?: return false
        updateTree()
        select(file, null)
        return true
    }

    fun createNewFile(project: Project?, directory: VirtualFile): VirtualFile? {
        val newFileNameWithExtension =
            if (fileType is UnknownFileType || newFileName.contains(".")) newFileName
            else newFileName + "." + fileType.defaultExtension

        var result: VirtualFile? = null
        val command = {
            ApplicationManager.getApplication().runWriteAction {
                try {
                    val file = directory.createChildData(this, newFileNameWithExtension)
                    VfsUtil.saveText(file, fileContent)
                    result = file
                } catch (ignored: IOException) {
                }
            }
        }
        CommandProcessor.getInstance().executeCommand(project, command, "Create $newFileNameWithExtension", null)
        return result
    }
}
