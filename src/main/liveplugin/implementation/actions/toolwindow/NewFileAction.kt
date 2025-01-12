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
 * Originally forked from com.intellij.openapi.fileChooser.actions.NewFileAction
 */
open class NewFileAction(text: String, private val fileType: FileType) : FileChooserAction(text, text, fileType.icon) {

    override fun update(fileSystemTree: FileSystemTree, e: AnActionEvent) {
        e.presentation.let {
            val selectedFile = fileSystemTree.newFileParent
            it.isEnabled =
                selectedFile != null &&
                    selectedFile != LocalFileSystem.getInstance().findFileByPath(LivePluginPaths.livePluginsPath.value)
            it.isVisible = true
        }
    }

    override fun actionPerformed(fileSystemTree: FileSystemTree, e: AnActionEvent) {
        val initialContent = e.getData(FileChooserKeys.NEW_FILE_TEMPLATE_TEXT) ?: ""
        createNewFile(fileSystemTree, e.project, fileType, initialContent)
    }

    private fun createNewFile(fileSystemTree: FileSystemTree, project: Project?, fileType: FileType?, initialContent: String) {
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

            val failReason = createNewFile(project, fileSystemTree as FileSystemTreeImpl, file, newFileName, fileType, initialContent)
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
        // modified copy of com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl.createNewFile()
        fun createNewFile(
            project: Project?,
            fileSystemTree: FileSystemTreeImpl,
            parentDirectory: VirtualFile,
            newFileName: String,
            fileType: FileType?,
            initialContent: String?
        ): Exception? {
            val failReason = arrayOf<Exception?>(null)
            val command = {
                ApplicationManager.getApplication().runWriteAction(object : Runnable {
                    override fun run() {
                        try {
                            var newFileNameWithExtension = newFileName
                            if (fileType != null && fileType !is UnknownFileType) {
                                newFileNameWithExtension = if (newFileName.endsWith('.'.toString() + fileType.defaultExtension)) newFileName else newFileName + '.' + fileType.defaultExtension
                            }
                            val file = parentDirectory.createChildData(this, newFileNameWithExtension)
                            VfsUtil.saveText(file, initialContent ?: "")
                            fileSystemTree.updateTree()
                            fileSystemTree.select(file, null)
                        } catch (e: IOException) {
                            failReason[0] = e
                        }
                    }
                })
            }
            CommandProcessor.getInstance().executeCommand(project, command, UIBundle.message("file.chooser.create.new.file.command.name"), null)
            return failReason[0]
        }
    }
}