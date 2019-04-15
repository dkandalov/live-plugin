package liveplugin.pluginrunner.kotlin

import liveplugin.LivePluginAppComponent.Companion.livePluginLibsPath
import java.io.File
import kotlin.script.experimental.intellij.ScriptDefinitionsProvider

class LivePluginKotlinScriptProvider: ScriptDefinitionsProvider {
    override val id = "LivePluginKotlinScriptProvider"
    override fun getDefinitionClasses() = listOf(KotlinScriptTemplate::class.java.canonicalName)
    override fun getDefinitionsClassPath() = File(livePluginLibsPath).listFiles().toList()
    override fun useDiscovery() = false
}

