package liveplugin

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "LivePluginSettings", storages = [Storage(value = "\$APP_CONFIG$/live-plugin.xml")])
data class Settings(
    var justInstalled: Boolean = true,
    var runPluginsOnProjectOpen: Boolean = false,
    var runAllPluginsOnIDEStartup: Boolean = false
): PersistentStateComponent<Settings> {

    override fun getState() = this

    override fun loadState(settings: Settings) {
        XmlSerializerUtil.copyBean(settings, this)
    }

    companion object {
        val instance: Settings
            get() = ServiceManager.getService(Settings::class.java)
    }
}