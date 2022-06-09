package liveplugin.implementation.actions.toolwindow

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileChooser.FileSystemTree
import com.intellij.openapi.fileChooser.actions.FileChooserAction
import com.intellij.openapi.fileChooser.tree.FileNode
import liveplugin.implementation.common.IdeUtil.runLaterOnEdt
import liveplugin.implementation.common.IdeUtil.showErrorDialog
import liveplugin.implementation.common.IdeUtil.showInputDialog
import java.io.IOException
import java.util.*
import kotlin.reflect.full.functions
import kotlin.reflect.jvm.isAccessible

class RenameFileAction: FileChooserAction("Rename", "", null) {

    override fun actionPerformed(fileSystemTree: FileSystemTree, event: AnActionEvent) {
        val file = fileSystemTree.selectedFile ?: return

        val defaultName = file.name
        val newFileName = event.project.showInputDialog(message = "Rename file to:", title = "Rename", initialValue = defaultName)
        if (newFileName == null || newFileName == file.name) return

        /**
         * Couldn't find any other non-hacky way to update file chooser tree to show new file name
         * (because com.intellij.openapi.fileChooser.tree.FileTreeModel.process code is unfinished and has todos).
         *
         * There is a problem with this hack that if new file name is longer than previous name,
         * it's not shown fully. The workaround is to collapse, expand parent tree node.
         */
        fun updateTreeModel_HACK() {
            val model = fileSystemTree.tree.model
            val queue = LinkedList<FileNode?>()
            queue.add(model.root as? FileNode)

            while (queue.isNotEmpty()) {
                val node = queue.remove() ?: continue

                val nodeContainsRenamedFile = file == node.file
                if (nodeContainsRenamedFile) {
                    runLaterOnEdt {
                        val kFunction = FileNode::class.functions.find { it.name == "updateName" }!!
                        kFunction.isAccessible = true
                        kFunction.call(node, file.name)
                        Unit
                    }
                    return
                }

                (0 until model.getChildCount(node)).forEach { i ->
                    // Do a soft cast to FileNode because it's been observed that children can also be a LoadingNode.
                    queue.add(model.getChild(node, i) as? FileNode)
                }
            }
        }

        runWriteAction {
            try {
                file.rename(null, newFileName)
                updateTreeModel_HACK()
            } catch (e: IOException) {
                event.project.showErrorDialog("Couldn't rename ${file.name} to $newFileName", "Error")
            }
        }
    }

    override fun update(fileChooser: FileSystemTree, event: AnActionEvent) {
        event.presentation.isVisible = true
        event.presentation.isEnabled = fileChooser.selectionExists()
    }
}
