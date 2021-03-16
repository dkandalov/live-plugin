package liveplugin.toolwindow.util

import liveplugin.LivePluginAppComponent
import liveplugin.LivePluginPaths
import java.io.IOException

class ExamplePluginInstaller(private val pluginPath: String, private val filePaths: List<String>) {

    fun installPlugin(handleError: (e: Exception, pluginPath: String) -> Unit) {
        val pluginId = extractPluginIdFrom(pluginPath)

        filePaths.forEach { relativeFilePath ->
            try {
                val text = LivePluginAppComponent.readSampleScriptFile(pluginPath, relativeFilePath)
                val (parentPath, fileName) = splitIntoPathAndFileName("${LivePluginPaths.livePluginsPath}/$pluginId/$relativeFilePath")
                createFile(parentPath, fileName, text)
            } catch (e: IOException) {
                handleError(e, pluginPath)
            }
        }
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
