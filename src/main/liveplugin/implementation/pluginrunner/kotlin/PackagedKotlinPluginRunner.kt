package liveplugin.implementation.pluginrunner.kotlin

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.WindowManager
import liveplugin.pluginrunner.Binding

class PackagedKotlinPluginRunner: AppLifecycleListener, DynamicPluginListener {
    init {
        val hasVisibleFrame = WindowManager.getInstance().findVisibleFrame() != null
        logger.info("init, hasVisibleFrame=$hasVisibleFrame")
        if (hasVisibleFrame) {
            invokeLater {
                loadPlugin()
            }
        }
    }

    override fun appFrameCreated(commandLineArgs: List<String>) {
        logger.info("appFrameCreated")
        invokeLater {
            loadPlugin()
        }
    }

    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        logger.info("beforePluginUnload")
        if (pluginDescriptor.pluginId == pluginId) {
            unloadPlugin()
        }
    }

    companion object {
        private val pluginId = (this::class.java.classLoader as? PluginAwareClassLoader)?.pluginId
        private val logger = Logger.getInstance("packaged-liveplugin.$pluginId")
        private var pluginDisposable: Disposable? = null

        private fun loadPlugin() {
            if (pluginDisposable != null) return
            logger.info("Loading ${Thread.currentThread()}")

            val binding = Binding(
                project = null,
                isIdeStartup = true,
                pluginPath = "",
                pluginDisposable = object: Disposable {
                    override fun dispose() {}
                    override fun toString() = "Packaged LivePlugin: $pluginId"
                }
            )
            val pluginClass = this::class.java.classLoader.loadClass("Plugin")
            val executable = KotlinPluginRunner.ExecutableKotlinPlugin(pluginClass)
            KotlinPluginRunner.main.run(executable, binding)

            pluginDisposable = binding.pluginDisposable
        }

        private fun unloadPlugin() {
            pluginDisposable?.let {
                logger.info("Unloading ${Thread.currentThread()}")
                Disposer.dispose(it)
                pluginDisposable = null
            }
        }
    }
}