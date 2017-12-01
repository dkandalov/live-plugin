package liveplugin

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager.getHomePath
import com.intellij.openapi.application.PathManager.getPluginsPath
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt.toSystemIndependentName
import com.intellij.openapi.vfs.VirtualFile
import liveplugin.IdeUtil.askIfUserWantsToRestartIde
import liveplugin.IdeUtil.downloadFile
import liveplugin.pluginrunner.*
import liveplugin.pluginrunner.kotlin.KotlinPluginRunner
import liveplugin.toolwindow.PluginToolWindowManager
import liveplugin.toolwindow.util.ExamplePluginInstaller
import java.io.File
import java.io.IOException

class LivePluginAppComponent: ApplicationComponent, DumbAware {

    override fun initComponent() {
        checkThatGroovyIsOnClasspath()

        val settings = Settings.getInstance()
        if (settings.justInstalled) {
            installHelloWorldPlugins()
            settings.justInstalled = false
        }
        if (settings.runAllPluginsOnIDEStartup) {
            runAllPlugins()
        }

        PluginToolWindowManager().init()
    }

    override fun disposeComponent() {}

    override fun getComponentName() = javaClass.simpleName!!

    companion object {

        val livePluginId = "LivePlugin"
        val groovyExamplesPath = "/groovy/"
        val kotlinExamplesPath = "/kotlin/"
        val livePluginLibsPath = toSystemIndependentName(getPluginsPath() + "/LivePlugin/lib/")
        val livePluginCompilerLibsPath = toSystemIndependentName(getPluginsPath() + "/LivePlugin/lib/kotlin-compiler")
        @JvmField val livePluginsPath = toSystemIndependentName(getPluginsPath() + "/live-plugins")
        val livePluginsClassesPath = toSystemIndependentName(getPluginsPath() + "/live-plugins-classes")
        val ideJarsPath = toSystemIndependentName(getHomePath() + "/lib")

        val livePluginNotificationGroup = NotificationGroup.balloonGroup("Live Plugin")

        private val logger = Logger.getInstance(LivePluginAppComponent::class.java)

        private val defaultIdeaOutputFolder = "out"

        fun pluginIdToPathMap(): Map<String, String> {
            val containsIdeaProjectFolder = File(livePluginsPath + "/" + DIRECTORY_STORE_FOLDER).exists()

            val files = File(livePluginsPath)
                .listFiles { file ->
                    file.isDirectory &&
                    file.name != DIRECTORY_STORE_FOLDER &&
                    !(containsIdeaProjectFolder && file.name == defaultIdeaOutputFolder)
                } ?: return emptyMap()

            return files.associate { Pair(it.name, toSystemIndependentName(it.absolutePath)) }
        }

        @JvmStatic fun isInvalidPluginFolder(virtualFile: VirtualFile): Boolean {
            val scriptFiles = listOf(
                GroovyPluginRunner.mainScript,
                ClojurePluginRunner.mainScript,
                ScalaPluginRunner.mainScript,
                KotlinPluginRunner.mainScript
            )
            return scriptFiles.none { MyFileUtil.findScriptFilesIn(virtualFile.path, it).isNotEmpty() }
        }

        fun defaultPluginScript(): String = readSampleScriptFile(groovyExamplesPath, "default-plugin.groovy")

        fun defaultPluginTestScript(): String = readSampleScriptFile(groovyExamplesPath, "default-plugin-test.groovy")

        fun readSampleScriptFile(pluginPath: String, file: String): String {
            return try {
                val path = pluginPath + file
                val inputStream = LivePluginAppComponent::class.java.classLoader.getResourceAsStream(path) ?: throw IllegalStateException("Could find resource for '$path'.")
                FileUtil.loadTextAndClose(inputStream)
            } catch (e: IOException) {
                logger.error(e)
                ""
            }
        }

        fun pluginExists(pluginId: String): Boolean = pluginIdToPathMap().keys.contains(pluginId)

        private val isGroovyOnClasspath: Boolean
            get() = IdeUtil.isOnClasspath("org.codehaus.groovy.runtime.DefaultGroovyMethods")

        fun scalaIsOnClassPath(): Boolean = IdeUtil.isOnClasspath("scala.Some")

        fun clojureIsOnClassPath(): Boolean = IdeUtil.isOnClasspath("clojure.core.Vec")

        private fun runAllPlugins() {
            ApplicationManager.getApplication().invokeLater {
                val event = AnActionEvent(
                    null,
                    IdeUtil.dummyDataContext,
                    PluginRunner.ideStartup,
                    Presentation(),
                    ActionManager.getInstance(),
                    0
                )
                val errorReporter = ErrorReporter()
                val pluginPaths = pluginIdToPathMap().keys.map { LivePluginAppComponent.pluginIdToPathMap()[it]!! }
                runPlugins(pluginPaths, event, errorReporter, createPluginRunners(errorReporter))
            }
        }

        fun checkThatGroovyIsOnClasspath(): Boolean {
            if (isGroovyOnClasspath) return true

            // this can be useful for non-java IDEs because they don't have bundled groovy libs
            val listener = NotificationListener { notification, _ ->
                val groovyVersion = "2.4.12" // version of groovy used by latest IJ
                val downloaded = downloadFile("http://repo1.maven.org/maven2/org/codehaus/groovy/groovy-all/$groovyVersion/", "groovy-all-$groovyVersion.jar", livePluginLibsPath)
                if (downloaded) {
                    notification.expire()
                    askIfUserWantsToRestartIde("For Groovy libraries to be loaded IDE restart is required. Restart now?")
                } else {
                    livePluginNotificationGroup
                        .createNotification("Failed to download Groovy libraries", NotificationType.WARNING)
                }
            }
            livePluginNotificationGroup.createNotification(
                "LivePlugin didn't find Groovy libraries on classpath",
                "Without it plugins won't work. <a href=\"\">Download Groovy libraries</a> (~7Mb)",
                NotificationType.ERROR,
                listener
            ).notify(null)

            return false
        }

        private fun installHelloWorldPlugins() {
            val loggingListener = object: ExamplePluginInstaller.Listener {
                override fun onException(e: Exception, pluginPath: String) {
                    logger.warn("Failed to install plugin: " + pluginPath, e)
                }
            }
            ExamplePluginInstaller(groovyExamplesPath + "hello-world/", listOf("plugin.groovy")).installPlugin(loggingListener)
            ExamplePluginInstaller(groovyExamplesPath + "ide-actions/", listOf("plugin.groovy")).installPlugin(loggingListener)
            ExamplePluginInstaller(groovyExamplesPath + "insert-new-line-above/", listOf("plugin.groovy")).installPlugin(loggingListener)
            ExamplePluginInstaller(groovyExamplesPath + "popup-menu/", listOf("plugin.groovy")).installPlugin(loggingListener)
        }
    }
}
