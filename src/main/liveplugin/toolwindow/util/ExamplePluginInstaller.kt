package liveplugin.toolwindow.util

import com.intellij.openapi.util.Pair
import liveplugin.LivePluginAppComponent
import liveplugin.LivePluginAppComponent.Companion.livepluginsPath
import java.io.IOException

class ExamplePluginInstaller(private val pluginPath: String, private val filePaths: List<String>) {

    fun installPlugin(listener: Listener) {
        val pluginId = extractPluginIdFrom(pluginPath)

        for (relativeFilePath in filePaths) {
            try {

                val text = LivePluginAppComponent.readSampleScriptFile(pluginPath, relativeFilePath)
                val pair = splitIntoPathAndFileName("$livepluginsPath/$pluginId/$relativeFilePath")
                PluginsIO.createFile(pair.first, pair.second, text)

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
            return Pair.create(filePath.substring(0, index), filePath.substring(index + 1))
        }

        fun extractPluginIdFrom(pluginPath: String): String {
            val split = pluginPath.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            return split[split.size - 1]
        }
    }
}
