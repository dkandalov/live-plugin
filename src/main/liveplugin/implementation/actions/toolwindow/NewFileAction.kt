package liveplugin.implementation.actions.toolwindow

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.fileChooser.FileSystemTree
import com.intellij.openapi.fileChooser.actions.FileChooserAction
import com.intellij.openapi.fileChooser.ex.FileChooserKeys
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.UIBundle
import liveplugin.implementation.LivePluginPaths
import java.io.IOException

/**
 * Originally based on com.intellij.openapi.fileChooser.actions.NewFileAction
 */
open class NewFileAction(text: String, private val fileType: FileType) : FileChooserAction(text, text, fileType.icon) {

    override fun update(fileSystemTree: FileSystemTree, e: AnActionEvent) {
        val selectedFile = fileSystemTree.newFileParent
        e.presentation.isEnabled =
            selectedFile != null && selectedFile != LocalFileSystem.getInstance().findFileByPath(LivePluginPaths.livePluginsPath.value)
        e.presentation.isVisible = true
    }

    override fun actionPerformed(fileSystemTree: FileSystemTree, e: AnActionEvent) {
        val initialContent = e.getData(FileChooserKeys.NEW_FILE_TEMPLATE_TEXT) ?: ""
        createNewFile(fileSystemTree, e.project, fileType, initialContent)
    }

    private fun createNewFile(fileSystemTree: FileSystemTree, project: Project?, fileType: FileType, fileContent: String) {
        var file = fileSystemTree.newFileParent ?: return
        if (!file.isDirectory) file = file.parent

        var newFileName: String?
        while (true) {
            newFileName = Messages.showInputDialog(
                UIBundle.message("create.new.file.enter.new.file.name.prompt.text"),
                UIBundle.message("new.file.dialog.title"), Messages.getQuestionIcon()
            )
            if (newFileName == null) return
            if (newFileName.trim { it <= ' ' }.isEmpty()) {
                Messages.showMessageDialog(
                    UIBundle.message("create.new.file.file.name.cannot.be.empty.error.message"),
                    UIBundle.message("error.dialog.title"), Messages.getErrorIcon()
                )
                continue
            }

            val failReason =
                createNewFile(project, fileSystemTree as FileSystemTreeImpl, file, newFileName, fileType, fileContent)
            if (failReason != null) {
                Messages.showMessageDialog(
                    UIBundle.message("create.new.file.could.not.create.file.error.message", newFileName),
                    UIBundle.message("error.dialog.title"), Messages.getErrorIcon()
                )
                continue
            }
            return
        }
    }

    companion object {
        private fun createNewFile(
            project: Project?,
            fileSystemTree: FileSystemTree,
            directory: VirtualFile,
            newFileName: String,
            fileType: FileType,
            fileContent: String
        ): Exception? {
            val newFileNameWithExtension =
                if (fileType is UnknownFileType || newFileName.endsWith("." + fileType.defaultExtension)) newFileName
                else newFileName + "." + fileType.defaultExtension

            var failReason: Exception? = null
            val command = {
                ApplicationManager.getApplication().runWriteAction {
                    try {
                        val file = directory.createChildData(this, newFileNameWithExtension)
                        VfsUtil.saveText(file, fileContent)
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
}