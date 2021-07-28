package liveplugin.toolwindow.util

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import liveplugin.LivePluginAppComponent.Companion.livePluginId
import java.io.IOException

private const val requestor = livePluginId

fun createFile(parentPath: String, fileName: String, text: String, whenCreated: (VirtualFile) -> Unit = {}) {
    runIOAction("createFile") {
        val parentFolder = VfsUtil.createDirectoryIfMissing(parentPath) ?: throw IOException("Failed to create folder $parentPath")
        if (parentFolder.findChild(fileName) == null) {
            val virtualFile = parentFolder.createChildData(requestor, fileName)
            VfsUtil.saveText(virtualFile, text)
            whenCreated(virtualFile)
        }
    }
}

fun VirtualFile.delete() {
    runIOAction("delete") {
        this.delete(requestor)
    }
}

private fun runIOAction(actionName: String, f: () -> Unit) {
    var exception: IOException? = null
    runWriteAction {
        CommandProcessor.getInstance().executeCommand(null, {
            try {
                f()
            } catch (e: IOException) {
                exception = e
            }
        }, actionName, livePluginId)
    }

    if (exception != null) throw exception!!
}
