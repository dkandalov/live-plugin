package liveplugin.toolwindow.popup

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileChooser.FileSystemTree
import com.intellij.openapi.fileChooser.actions.FileChooserAction
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.UIBundle
import liveplugin.LivePluginAppComponent
import liveplugin.LivePluginAppComponent.Companion.findPluginFolder
import liveplugin.toFilePath
import java.io.IOException
import javax.swing.Icon

open class NewFileFromTemplateAction(
    text: String,
    private val newFileName: String,
    private val fileContent: String,
    icon: Icon?,
    private val fileType: FileType
): FileChooserAction(text, text, icon) {

    override fun update(fileSystemTree: FileSystemTree, event: AnActionEvent) {
        val parentFile = fileSystemTree.newFileParent?.directory()

        val enabled = parentFile != null &&
            parentFile.findChild(newFileName) == null &&
            parentFile.toFilePath().findPluginFolder() in LivePluginAppComponent.pluginIdToPathMap().values


        event.presentation.apply {
            isEnabled = enabled
            isVisible = enabled
            icon = fileType.icon
        }
    }

    override fun actionPerformed(fileSystemTree: FileSystemTree, event: AnActionEvent) {
        val parentFile = fileSystemTree.newFileParent?.parent ?: return

        val failReason = createNewFile(event.project, fileSystemTree as FileSystemTreeImpl, parentFile, newFileName, fileType, fileContent)
        if (failReason != null) {
            val message = UIBundle.message("create.new.file.could.not.create.file.error.message", newFileName)
            val title = UIBundle.message("error.dialog.title")
            Messages.showMessageDialog(message, title, Messages.getErrorIcon())
        }
    }

    private fun VirtualFile.directory() = if (isDirectory) this else parent

    private fun createNewFile(
        project: Project?,
        fileSystemTree: FileSystemTreeImpl,
        parentDirectory: VirtualFile,
        newFileName: String,
        fileType: FileType?,
        initialContent: String = ""
    ): Exception? {
        val newFileNameWithExtension =
            if (fileType == null || fileType is UnknownFileType || newFileName.contains(".")) newFileName
            else newFileName + "." + fileType.defaultExtension

        var failReason: Exception? = null
        val command = {
            ApplicationManager.getApplication().runWriteAction {
                try {
                    val file = parentDirectory.createChildData(this, newFileNameWithExtension)
                    VfsUtil.saveText(file, initialContent)
                    fileSystemTree.updateTree()
                    fileSystemTree.select(file, null)
                } catch (e: IOException) {
                    failReason = e
                }
            }
        }
        CommandProcessor.getInstance().executeCommand(project, command, "Create $newFileNameWithExtension", null)
        return failReason
    }
}
