package liveplugin.toolwindow.popup

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileSystemTree
import com.intellij.openapi.fileChooser.actions.FileChooserAction
import com.intellij.openapi.fileChooser.ex.FileSystemTreeImpl
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.ui.Messages
import com.intellij.ui.UIBundle
import liveplugin.LivePluginAppComponent
import javax.swing.Icon

open class NewFileFromTemplateAction(text: String, private val newFileName: String, private val fileContent: String, icon: Icon?, private val fileType: FileType): FileChooserAction(text, text, icon) {

    override fun update(fileSystemTree: FileSystemTree, event: AnActionEvent) {
        val parentFile = fileSystemTree.newFileParent

        val isAtPluginRoot = LivePluginAppComponent.pluginIdToPathMap().containsValue(parentFile?.canonicalPath)
        val fileDoesNotExist = if (isAtPluginRoot) parentFile?.findChild(newFileName) == null else false

        event.presentation.apply {
            isEnabled = isAtPluginRoot && fileDoesNotExist
            isVisible = isAtPluginRoot && fileDoesNotExist
            icon = fileType.icon
        }
    }

    override fun actionPerformed(fileSystemTree: FileSystemTree, event: AnActionEvent) {
        val parentFile = fileSystemTree.newFileParent

        val failReason = (fileSystemTree as FileSystemTreeImpl).createNewFile(parentFile, newFileName, fileType, fileContent)
        if (failReason != null) {
            val message = UIBundle.message("create.new.file.could.not.create.file.error.message", newFileName)
            val title = UIBundle.message("error.dialog.title")
            Messages.showMessageDialog(message, title, Messages.getErrorIcon())
        }
    }
}