package liveplugin.pluginrunner.kotlin

import com.intellij.ide.AppLifecycleListener
import com.intellij.openapi.application.ApplicationManager
import liveplugin.pluginrunner.Binding

class PackagedKotlinPluginRunner: AppLifecycleListener {
    override fun appFrameCreated(commandLineArgs: List<String>) {
        val binding = Binding(
            project = null,
            isIdeStartup = true,
            pluginPath = "",
            pluginDisposable = ApplicationManager.getApplication()
        )
        val pluginClass = this::class.java.classLoader.loadClass("Plugin")
        val executable = KotlinPluginRunner.ExecutableKotlinPlugin(pluginClass)
        KotlinPluginRunner.main.run(executable, binding)
    }
}