package liveplugin.toolwindow.popup

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileElement
import com.intellij.openapi.fileChooser.FileSystemTree
import com.intellij.openapi.fileChooser.actions.FileChooserAction
import com.intellij.openapi.fileChooser.ex.FileNodeDescriptor
import com.intellij.openapi.ui.Messages
import liveplugin.IdeUtil
import java.io.IOException
import java.util.*
import javax.swing.SwingUtilities
import javax.swing.tree.DefaultMutableTreeNode

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
             * Couldn't find any way to update file chooser tree to show new file name, therefore this hack.
             * There is still a problem with this except that it's a hack.
             * If new file name is longer than previous name, it's not shown fully.
             * The workaround is to collapse, expand parent tree node.
             */
            private fun updateTreeModel_HACK() {
                val model = fileSystemTree.tree.model
                val queue = LinkedList<DefaultMutableTreeNode>()
                var node = model.root as DefaultMutableTreeNode
                queue.add(node)

                while (!queue.isEmpty()) {
                    node = queue.remove()
                    val userObject = node.userObject
                    val nodeContainsRenamedFile = userObject is FileNodeDescriptor && file == userObject.element.file

                    if (nodeContainsRenamedFile) {
                        val finalNode = node
                        SwingUtilities.invokeLater {
                            val nodeDescriptor = userObject as FileNodeDescriptor
                            val fileElement = FileElement(file, newFileName)
                            fileElement.parent = nodeDescriptor.element.parent
                            finalNode.userObject = FileNodeDescriptor(
                                nodeDescriptor.project,
                                fileElement,
                                nodeDescriptor.parentDescriptor,
                                nodeDescriptor.icon,
                                newFileName,
                                nodeDescriptor.comment
                            )
                        }
                        return
                    }

                    for (i in 0 until model.getChildCount(node)) {
                        queue.add(model.getChild(node, i) as DefaultMutableTreeNode)
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
