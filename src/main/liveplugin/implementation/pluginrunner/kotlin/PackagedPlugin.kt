package liveplugin.implementation.pluginrunner.kotlin

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.WindowManager
import liveplugin.implementation.common.IdeUtil.runLaterOnEdt
import liveplugin.implementation.common.IdeUtil.runOnEdt
import liveplugin.implementation.pluginrunner.Binding
import liveplugin.implementation.pluginrunner.kotlin.KotlinPluginRunner.Companion.mainKotlinPluginRunner
import liveplugin.implementation.pluginrunner.kotlin.KotlinPluginRunner.ExecutableKotlinPlugin

class PackagedPluginAppLifecycle: AppLifecycleListener {
    @Suppress("UnstableApiUsage")
    override fun appStarted() {
        logger.info("PackagedPluginAppLifecycle.appStarted")
        runLaterOnEdt { loadPlugin() }
    }

    override fun appWillBeClosed(isRestart: Boolean) {
        logger.info("PackagedPluginAppLifecycle.appWillBeClosed")
        // Not running "later" so that it happens before IDE shutdown.
        runOnEdt { unloadPlugin() }
    }
}

class PackagedPluginDynamicLifecycle: DynamicPluginListener {
    // Invoked when plugin installed for the first time or enabled in IDE settings.
    init {
        val hasVisibleFrame = WindowManager.getInstance().findVisibleFrame() != null
        logger.info("PackagedPluginDynamicLifecycle, hasVisibleFrame=$hasVisibleFrame")
        if (hasVisibleFrame) {
            runLaterOnEdt { loadPlugin() }
        }
    }

    // Invoked when plugin is disabled in IDE settings.
    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        logger.info("PackagedPluginDynamicLifecycle.beforePluginUnload")
        if (pluginDescriptor.pluginId == packagedPluginId) {
            runLaterOnEdt { unloadPlugin() }
        }
    }
}

private val packagedPluginId = (PackagedPluginAppLifecycle::class.java.classLoader as? PluginAwareClassLoader)?.pluginId ?: "some-plugin"
private val logger = Logger.getInstance("packaged-liveplugin.$packagedPluginId")
private var pluginDisposable: Disposable? = null

private fun loadPlugin() {
    if (pluginDisposable != null) return
    logger.info("Loading on ${Thread.currentThread()}")

    val binding = Binding(
        project = null,
        isIdeStartup = true,
        pluginPath = "",
        pluginDisposable = object: Disposable {
            override fun dispose() {}
            override fun toString() = "Packaged LivePlugin: $packagedPluginId"
        }
    )
    val pluginClass = PackagedPluginAppLifecycle::class.java.classLoader.loadClass("Plugin")
    val executable = ExecutableKotlinPlugin(pluginClass)
    mainKotlinPluginRunner.run(executable, binding)

    pluginDisposable = binding.pluginDisposable
}

private fun unloadPlugin() {
    pluginDisposable?.let {
        logger.info("Unloading on ${Thread.currentThread()}")
        Disposer.dispose(it)
        pluginDisposable = null
    }
}
