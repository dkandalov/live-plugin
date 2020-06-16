package liveplugin

import com.intellij.ide.AppLifecycleListener
import com.intellij.lang.*
import com.intellij.notification.*
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.PathManager.*
import com.intellij.openapi.diagnostic.*
import com.intellij.openapi.fileEditor.impl.*
import com.intellij.openapi.fileTypes.*
import com.intellij.openapi.module.*
import com.intellij.openapi.project.*
import com.intellij.openapi.project.Project.*
import com.intellij.openapi.util.*
import com.intellij.openapi.util.io.*
import com.intellij.openapi.util.io.FileUtilRt.*
import com.intellij.openapi.vfs.*
import com.intellij.psi.*
import com.intellij.psi.search.*
import com.intellij.psi.util.*
import com.intellij.usages.impl.rules.*
import com.intellij.util.indexing.*
import liveplugin.IdeUtil.askIfUserWantsToRestartIde
import liveplugin.IdeUtil.downloadFile
import liveplugin.IdeUtil.dummyDataContext
import liveplugin.IdeUtil.ideStartupActionPlace
import liveplugin.IdeUtil.invokeLaterOnEDT
import liveplugin.LivePluginPaths.groovyExamplesPath
import liveplugin.LivePluginPaths.livePluginsPath
import liveplugin.pluginrunner.RunPluginAction.Companion.runPlugins
import liveplugin.pluginrunner.groovy.*
import liveplugin.pluginrunner.kotlin.*
import liveplugin.toolwindow.util.*
import java.io.*
import kotlin.Pair

object LivePluginPaths {
    val ideJarsPath = toSystemIndependentName(getHomePath() + "/lib")

    val livePluginPath = toSystemIndependentName(getPluginsPath() + "/LivePlugin/")
    val livePluginLibPath = toSystemIndependentName(getPluginsPath() + "/LivePlugin/lib/")
    val livePluginsCompiledPath = toSystemIndependentName(getPluginsPath() + "/live-plugins-compiled")
    @JvmField val livePluginsPath = toSystemIndependentName(getPluginsPath() + "/live-plugins")

    const val groovyExamplesPath = "/groovy/"
    const val kotlinExamplesPath = "/kotlin/"
}

class LivePluginAppComponent : AppLifecycleListener {
    override fun appFrameCreated(commandLineArgs: MutableList<String>) {
        checkThatGroovyIsOnClasspath()

        val settings = Settings.getInstance()
        if (settings.justInstalled) {
            installHelloWorldPlugins()
            settings.justInstalled = false
        }
        if (settings.runAllPluginsOnIDEStartup) {
            runAllPlugins()
        }
    }

    companion object {
        const val livePluginId = "LivePlugin"
        private val logger = Logger.getInstance(LivePluginAppComponent::class.java)
        private val livePluginNotificationGroup = NotificationGroup.balloonGroup("Live Plugin")

        private const val defaultIdeaOutputFolder = "out"

        fun pluginIdToPathMap(): Map<String, String> {
            val containsIdeaProjectFolder = File("$livePluginsPath/$DIRECTORY_STORE_FOLDER").exists()

            val files = File(livePluginsPath)
                .listFiles { file ->
                    file.isDirectory &&
                    file.name != DIRECTORY_STORE_FOLDER &&
                    !(containsIdeaProjectFolder && file.name == defaultIdeaOutputFolder)
                } ?: return emptyMap()

            return files.associate { Pair(it.name, toSystemIndependentName(it.absolutePath)) }
        }

        fun isInvalidPluginFolder(virtualFile: VirtualFile): Boolean {
            val scriptFiles = listOf(
                GroovyPluginRunner.mainScript,
                KotlinPluginRunner.mainScript
            )
            return scriptFiles.none { findScriptFilesIn(virtualFile.path, it).isNotEmpty() }
        }

        fun findPluginRootsFor(files: Array<VirtualFile>): Set<VirtualFile> =
            files.mapNotNull { it.pluginFolder() }.toSet()

        private fun VirtualFile.pluginFolder(): VirtualFile? {
            val parent = parent ?: return null

            val pluginsRoot = File(livePluginsPath)
            // Compare with FileUtil because string comparison was observed to not work on windows (e.g. "c:/..." and "C:/...")
            return if (!FileUtil.filesEqual(File(parent.path), pluginsRoot)) parent.pluginFolder() else this
        }

        fun defaultPluginScript(): String = readSampleScriptFile(groovyExamplesPath, "default-plugin.groovy")

        fun defaultPluginTestScript(): String = readSampleScriptFile(groovyExamplesPath, "default-plugin-test.groovy")

        fun readSampleScriptFile(pluginPath: String, file: String): String {
            return try {
                val path = pluginPath + file
                val inputStream = LivePluginAppComponent::class.java.classLoader.getResourceAsStream(path) ?: error("Could find resource for '$path'.")
                FileUtil.loadTextAndClose(inputStream)
            } catch (e: IOException) {
                logger.error(e)
                ""
            }
        }

        fun pluginExists(pluginId: String): Boolean = pluginIdToPathMap().keys.contains(pluginId)

        private fun runAllPlugins() {
            invokeLaterOnEDT {
                val event = AnActionEvent(
                    null,
                    dummyDataContext,
                    ideStartupActionPlace,
                    Presentation(),
                    ActionManager.getInstance(),
                    0
                )
                val pluginPaths = pluginIdToPathMap().keys.map { pluginIdToPathMap().getValue(it) }
                runPlugins(pluginPaths, event)
            }
        }

        fun checkThatGroovyIsOnClasspath(): Boolean {
            if (isGroovyOnClasspath) return true

            // This can be useful for non-java IDEs because they don't have bundled groovy libs.
            val listener = NotificationListener { notification, _ ->
                val groovyVersion = "2.5.11" // Version of groovy used by latest IJ.
                val downloaded = downloadFile(
                    "https://repo1.maven.org/maven2/org/codehaus/groovy/groovy-all/$groovyVersion/",
                    "groovy-all-$groovyVersion.jar",
                    LivePluginPaths.livePluginLibPath
                )
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

        private val isGroovyOnClasspath: Boolean
            get() = IdeUtil.isOnClasspath("org.codehaus.groovy.runtime.DefaultGroovyMethods")

        private fun installHelloWorldPlugins() {
            val loggingListener = object: ExamplePluginInstaller.Listener {
                override fun onException(e: Exception, pluginPath: String) = logger.warn("Failed to install plugin: $pluginPath", e)
            }
            ExamplePluginInstaller(groovyExamplesPath + "hello-world/", listOf("plugin.groovy")).installPlugin(loggingListener)
            ExamplePluginInstaller(groovyExamplesPath + "ide-actions/", listOf("plugin.groovy")).installPlugin(loggingListener)
            ExamplePluginInstaller(groovyExamplesPath + "insert-new-line-above/", listOf("plugin.groovy")).installPlugin(loggingListener)
            ExamplePluginInstaller(groovyExamplesPath + "popup-menu/", listOf("plugin.groovy")).installPlugin(loggingListener)
        }
    }

    class MakePluginFilesAlwaysEditable: NonProjectFileWritingAccessExtension {
        override fun isWritable(file: VirtualFile) = file.isUnderLivePluginsPath()
    }

    class Highlighter: SyntaxHighlighterProvider {
        override fun create(fileType: FileType, project: Project?, file: VirtualFile?): SyntaxHighlighter? {
            if (project == null || file == null || !file.isUnderLivePluginsPath()) return null
            val language = LanguageUtil.getLanguageForPsi(project, file)
            return if (language == null) null else SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, file)
        }
    }

    class UsageTypeExtension: UsageTypeProvider {
        override fun getUsageType(element: PsiElement): UsageType? {
            val file = PsiUtilCore.getVirtualFile(element) ?: return null
            if (!file.isUnderLivePluginsPath()) return null
            return UsageType { "Usage in liveplugin" }
        }
    }

    class IndexSetContributor: IndexableSetContributor() {
        override fun getAdditionalRootsToIndex(): MutableSet<VirtualFile> {
            val path = livePluginsPath.findFileByUrl() ?: return HashSet()
            return mutableSetOf(path)
        }
    }

    class UseScopeExtension: UseScopeEnlarger() {
        override fun getAdditionalUseScope(element: PsiElement): SearchScope? {
            val useScope = element.useScope
            return if (useScope is LocalSearchScope) null else LivePluginsSearchScope.getScopeInstance(element.project)
        }
    }

    private class LivePluginsSearchScope(project: Project): GlobalSearchScope(project) {
        override fun getDisplayName() = "LivePlugins"
        override fun contains(file: VirtualFile) = file.isUnderLivePluginsPath()
        override fun isSearchInModuleContent(aModule: Module) = false
        override fun isSearchInLibraries() = false

        companion object {
            private val SCOPE_KEY = NotNullLazyKey.create<LivePluginsSearchScope, Project>("LIVEPLUGIN_SEARCH_SCOPE_KEY") { project ->
                LivePluginsSearchScope(project)
            }

            fun getScopeInstance(project: Project): GlobalSearchScope = SCOPE_KEY.getValue(project)
        }
    }
}

private fun VirtualFile.isUnderLivePluginsPath() = FileUtil.startsWith(path, livePluginsPath)

