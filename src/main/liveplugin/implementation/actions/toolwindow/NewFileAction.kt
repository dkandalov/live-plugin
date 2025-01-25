package liveplugin.implementation.actions.toolwindow

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileSystemTree
import com.intellij.openapi.fileChooser.actions.FileChooserAction
import com.intellij.openapi.fileChooser.ex.FileChooserKeys
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import liveplugin.implementation.LivePluginPaths
import liveplugin.implementation.common.createFile

/**
 * Originally based on [com.intellij.openapi.fileChooser.actions.NewFileAction]
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
        val directory = fileSystemTree.newFileParent
            ?.let { if (it.isDirectory) it else it.parent }
            ?: return

        val newFileName = Messages.showInputDialog(
            project,
            "Enter a new file name:",
            "New File",
            Messages.getQuestionIcon(),
            null,
            object : InputValidatorEx {
                override fun getErrorText(inputString: String) =
                    if (inputString.isEmpty()) "File name cannot be empty" else null
            }
        )?.let {
            if (fileType is UnknownFileType || it.endsWith("." + fileType.defaultExtension)) it
            else it + "." + fileType.defaultExtension
        } ?: return

        createFile(directory.path, newFileName, fileContent, whenCreated = { file ->
            fileSystemTree.updateTree()
            fileSystemTree.select(file, null)
        })
    }
}