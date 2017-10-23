package liveplugin

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import java.util.*

@Suppress("unused")
@State(name = "LivePluginSettings", storages = arrayOf(Storage("live-plugin.xml")))
class Settings(
    var justInstalled: Boolean = true,
    var runAllPluginsOnIDEStartup: Boolean = false,
    var pluginsUsage: MutableMap<String, Int> = HashMap()
): PersistentStateComponent<Settings> {

    override fun getState(): Settings? = this

    override fun loadState(settings: Settings) {
        XmlSerializerUtil.copyBean(settings, this)
    }

    companion object {
        val instance: Settings get() = ServiceManager.getService(Settings::class.java)

        fun countPluginsUsage(pluginIds: Collection<String>) {
            val pluginsUsage = Settings.instance.pluginsUsage
            for (pluginId in pluginIds) {
                val count = pluginsUsage[pluginId] ?: 0
                pluginsUsage.put(pluginId, count + 1)
            }
        }
    }
}