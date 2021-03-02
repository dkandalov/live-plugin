package liveplugin

import com.intellij.ide.AppLifecycleListener
import com.intellij.lang.LanguageUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.PathManager.getHomePath
import com.intellij.openapi.application.PathManager.getPluginsPath
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessExtension
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.fileTypes.SyntaxHighlighterProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER
import com.intellij.openapi.util.NotNullLazyKey
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UseScopeEnlarger
import com.intellij.psi.util.PsiUtilCore
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider
import com.intellij.util.indexing.IndexableSetContributor
import liveplugin.IdeUtil.askIfUserWantsToRestartIde
import liveplugin.IdeUtil.downloadFile
import liveplugin.IdeUtil.dummyDataContext
import liveplugin.IdeUtil.ideStartupActionPlace
import liveplugin.IdeUtil.invokeLaterOnEDT
import liveplugin.LivePluginPaths.groovyExamplesPath
import liveplugin.LivePluginPaths.livePluginsPath
import liveplugin.pluginrunner.RunPluginAction.Companion.runPlugins
import liveplugin.pluginrunner.groovy.GroovyPluginRunner
import liveplugin.pluginrunner.kotlin.KotlinPluginRunner
import liveplugin.toolwindow.util.ExamplePluginInstaller
import org.jetbrains.kotlin.idea.core.util.toVirtualFile
import java.io.IOException

object LivePluginPaths {
    val ideJarsPath = getHomePath().toFilePath() + "lib"

    val livePluginPath = getPluginsPath().toFilePath() + "LivePlugin"
    val livePluginLibPath = getPluginsPath().toFilePath() + "LivePlugin/lib"
    val livePluginsCompiledPath = getPluginsPath().toFilePath() + "live-plugins-compiled"
    @JvmField val livePluginsPath = getPluginsPath().toFilePath() + "live-plugins"

    const val groovyExamplesPath = "groovy/"
    const val kotlinExamplesPath = "kotlin/"
}

class LivePluginAppComponent : AppLifecycleListener {
    override fun appFrameCreated(commandLineArgs: MutableList<String>) {
        checkThatGroovyIsOnClasspath()

        val settings = Settings.instance
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
        private val livePluginNotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("Live Plugin")

        private const val defaultIdeaOutputFolder = "out"

        fun pluginIdToPathMap(): Map<String, FilePath> {
            val containsIdeaProjectFolder = (livePluginsPath + DIRECTORY_STORE_FOLDER).exists()

            val files = livePluginsPath
                .listFiles { file ->
                    file.isDirectory &&
                    file.name != DIRECTORY_STORE_FOLDER &&
                    !(containsIdeaProjectFolder && file.name == defaultIdeaOutputFolder)
                }

            return files.associate { Pair(it.name, it.toFilePath()) }
        }

        fun isInvalidPluginFolder(virtualFile: VirtualFile): Boolean =
            virtualFile.toFilePath().findAll(GroovyPluginRunner.mainScript).isEmpty() &&
            virtualFile.toFilePath().findAll(KotlinPluginRunner.mainScript).isEmpty()

        fun findPluginRootsFor(files: Array<VirtualFile>): Set<VirtualFile> =
            files.mapNotNull { it.pluginFolder() }.toSet()

        private fun VirtualFile.pluginFolder(): VirtualFile? {
            val parent = parent ?: return null
            return if (parent.toFilePath() == livePluginsPath) this else parent.pluginFolder()
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
            val actionManager = ActionManager.getInstance() // get ActionManager instance outside of EDT (because it failed in 201.6668.113)
            invokeLaterOnEDT {
                val event = AnActionEvent(
                    null,
                    dummyDataContext,
                    ideStartupActionPlace,
                    Presentation(),
                    actionManager,
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
            invokeLaterOnEDT {
                val loggingListener = object: ExamplePluginInstaller.Listener {
                    override fun onException(e: Exception, pluginPath: String) = logger.warn("Failed to install plugin: $pluginPath", e)
                }
                ExamplePluginInstaller(groovyExamplesPath + "hello-world/", listOf("plugin.groovy")).installPlugin(loggingListener)
                ExamplePluginInstaller(groovyExamplesPath + "ide-actions/", listOf("plugin.groovy")).installPlugin(loggingListener)
                ExamplePluginInstaller(groovyExamplesPath + "insert-new-line-above/", listOf("plugin.groovy")).installPlugin(loggingListener)
                ExamplePluginInstaller(groovyExamplesPath + "popup-menu/", listOf("plugin.groovy")).installPlugin(loggingListener)
            }
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
        override fun getAdditionalRootsToIndex(): Set<VirtualFile> {
            return mutableSetOf(livePluginsPath.toVirtualFile() ?: return HashSet())
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

private fun VirtualFile.isUnderLivePluginsPath() = FileUtil.startsWith(path, livePluginsPath.value)

