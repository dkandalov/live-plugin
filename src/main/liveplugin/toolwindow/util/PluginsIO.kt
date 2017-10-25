package liveplugin.toolwindow.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import java.io.IOException

object PluginsIO {
    private val requestor = PluginsIO::class.java.canonicalName

    @Throws(IOException::class)
    @JvmStatic fun createFile(parentPath: String, fileName: String, text: String) {
        runIOAction("createFile", {
            val parentFolder = VfsUtil.createDirectoryIfMissing(parentPath) ?: throw IOException("Failed to create folder " + parentPath)
            val file = parentFolder.createChildData(requestor, fileName)
            VfsUtil.saveText(file, text)
        })
    }

    @Throws(IOException::class)
    @JvmStatic fun delete(filePath: String) {
        runIOAction("delete", {
            val file = VirtualFileManager.getInstance().findFileByUrl("file://" + filePath) ?: throw IOException("Failed to find file " + filePath)
            file.delete(requestor)
        })
    }

    private fun runIOAction(actionName: String, f: () -> Unit) {
        var exception: IOException? = null
        CommandProcessor.getInstance().executeCommand(null, {
            ApplicationManager.getApplication().runWriteAction {
                try {
                    f()
                } catch (e: IOException) {
                    exception = e
                }
            }
        }, actionName, "LivePlugin")

        if (exception != null) throw exception!!
    }
}
