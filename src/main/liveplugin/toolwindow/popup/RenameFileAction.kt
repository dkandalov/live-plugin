package liveplugin.toolwindow.popup

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileSystemTree
import com.intellij.openapi.fileChooser.actions.FileChooserAction
import com.intellij.openapi.fileChooser.tree.FileNode
import com.intellij.openapi.ui.Messages
import liveplugin.IdeUtil
import liveplugin.IdeUtil.invokeLaterOnEDT
import java.io.IOException
import java.util.*
import kotlin.reflect.full.functions
import kotlin.reflect.jvm.isAccessible

class RenameFileAction: FileChooserAction() {

    override fun actionPerformed(fileSystemTree: FileSystemTree, event: AnActionEvent) {
        val file = fileSystemTree.selectedFile ?: return

        val defaultName = file.name
        val newFileName = Messages.showInputDialog("Rename file to:", "Rename", null, defaultName, null)
        if (newFileName == null || newFileName == file.name) return

        ApplicationManager.getApplication().runWriteAction(object: Runnable {
            override fun run() {
                try {
                    file.rename(null, newFileName)
                    updateTreeModel_HACK()
                } catch (e: IOException) {
                    IdeUtil.showErrorDialog(event.project, "Couldn't rename " + file.name + " to " + newFileName, "Error")
                }
            }

            /**
             * Couldn't find any other non-hacky way to update file chooser tree to show new file name
             * (because com.intellij.openapi.fileChooser.tree.FileTreeModel.process code is unfinished and has todos).
             *
             * There is a problem with this hack that if new file name is longer than previous name,
             * it's not shown fully. The workaround is to collapse, expand parent tree node.
             */
            private fun updateTreeModel_HACK() {
                val model = fileSystemTree.tree.model
                val queue = LinkedList<FileNode>()
                var node = model.root as FileNode
                queue.add(node)

                while (!queue.isEmpty()) {
                    node = queue.remove()

                    val nodeContainsRenamedFile = file == node.file
                    if (nodeContainsRenamedFile) {
                        invokeLaterOnEDT {
                            val kFunction = FileNode::class.functions.find { it.name == "updateName" }!!
                            kFunction.isAccessible = true
                            kFunction.call(node, file.name)
                            Unit
                        }
                        return
                    }

                    (0 until model.getChildCount(node)).forEach { i ->
                        queue.add(model.getChild(node, i) as FileNode)
                    }
                }
            }
        })
    }

    override fun update(fileChooser: FileSystemTree, event: AnActionEvent) {
        event.presentation.isVisible = true
        event.presentation.isEnabled = fileChooser.selectionExists()
    }
}
