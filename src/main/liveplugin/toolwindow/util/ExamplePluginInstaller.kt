package liveplugin.toolwindow.util

import liveplugin.LivePluginAppComponent
import liveplugin.LivePluginAppComponent.Companion.livePluginsPath
import java.io.IOException

class ExamplePluginInstaller(private val pluginPath: String, private val filePaths: List<String>) {

    fun installPlugin(listener: Listener) {
        val pluginId = extractPluginIdFrom(pluginPath)

        filePaths.forEach { relativeFilePath ->
            try {
                val text = LivePluginAppComponent.readSampleScriptFile(pluginPath, relativeFilePath)
                val (parentPath, fileName) = splitIntoPathAndFileName("$livePluginsPath/$pluginId/$relativeFilePath")
                createFile(parentPath, fileName, text)
            } catch (e: IOException) {
                listener.onException(e, pluginPath)
            }
        }
    }

    interface Listener {
        fun onException(e: Exception, pluginPath: String)
    }

    companion object {
        private fun splitIntoPathAndFileName(filePath: String): Pair<String, String> {
            val index = filePath.lastIndexOf("/")
            return Pair(filePath.substring(0, index), filePath.substring(index + 1))
        }

        fun extractPluginIdFrom(pluginPath: String): String {
            val split = pluginPath.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            return split[split.size - 1]
        }
    }
}
