package liveplugin

import com.intellij.ide.AppLifecycleListener
import com.intellij.lang.LanguageUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext.EMPTY_CONTEXT
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessExtension
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.fileTypes.SyntaxHighlighterProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.ui.Messages
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
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.indexing.IndexableSetContributor
import liveplugin.IdeUtil.ideStartupActionPlace
import liveplugin.IdeUtil.invokeLaterOnEDT
import liveplugin.LivePluginAppComponent.Companion.runAllPlugins
import liveplugin.LivePluginPaths.livePluginsPath
import liveplugin.LivePluginPaths.livePluginsProjectDirName
import liveplugin.pluginrunner.RunPluginAction.Companion.runPlugins
import liveplugin.pluginrunner.UnloadPluginAction.Companion.unloadPlugins
import liveplugin.pluginrunner.groovy.GroovyPluginRunner
import liveplugin.pluginrunner.kotlin.KotlinPluginRunner
import liveplugin.toolwindow.util.GroovyExamples
import liveplugin.toolwindow.util.installPlugin
import java.io.IOException

class LivePluginAppListener: AppLifecycleListener {
    override fun appFrameCreated(commandLineArgs: List<String>) {
        // If liveplugins call ActionManager before that app frame is created, it will fail with:
        // Should be called at least in the state COMPONENTS_LOADED, the current state is: CONFIGURATION_STORE_INITIALIZED

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

    private fun installHelloWorldPlugins() {
        invokeLaterOnEDT {
            listOf(GroovyExamples.helloWorld, GroovyExamples.ideActions, GroovyExamples.modifyDocument, GroovyExamples.popupMenu).forEach {
                it.installPlugin(handleError = { e: Exception, pluginPath: String ->
                    LivePluginAppComponent.logger.warn("Failed to install plugin: $pluginPath", e)
                })
            }
        }
    }

    private fun checkThatGroovyIsOnClasspath(): Boolean {
        val isGroovyOnClasspath = isOnClasspath("org.codehaus.groovy.runtime.DefaultGroovyMethods")
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
                LivePluginAppComponent.livePluginNotificationGroup
                    .createNotification("Failed to download Groovy libraries", NotificationType.WARNING)
            }
        }
        LivePluginAppComponent.livePluginNotificationGroup.createNotification(
            "LivePlugin didn't find Groovy libraries on classpath",
            "Without it plugins won't work. <a href=\"\">Download Groovy libraries</a> (~7Mb)",
            NotificationType.ERROR,
            listener
        ).notify(null)

        return false
    }

    private fun downloadFile(downloadUrl: String, fileName: String, targetPath: FilePath): Boolean =
        downloadFiles(listOf(Pair(downloadUrl, fileName)), targetPath)

    // TODO make download non-modal
    private fun downloadFiles(urlAndFileNames: List<Pair<String, String>>, targetPath: FilePath): Boolean {
        val service = DownloadableFileService.getInstance()
        val descriptions = ContainerUtil.map(urlAndFileNames) { service.createFileDescription(it.first + it.second, it.second) }
        val files = service.createDownloader(descriptions, "").downloadFilesWithProgress(targetPath.value, null, null)
        return files != null && files.size == urlAndFileNames.size
    }

    private fun askIfUserWantsToRestartIde(message: String) {
        val answer = Messages.showOkCancelDialog(message, "Restart Is Required", "Restart", "Postpone", Messages.getQuestionIcon())
        if (answer == Messages.OK) {
            ApplicationManagerEx.getApplicationEx().restart(true)
        }
    }

    private fun isOnClasspath(className: String) =
        LivePluginAppListener::class.java.classLoader.getResource(className.replace(".", "/") + ".class") != null
}

class LivePluginProjectListener : ProjectManagerListener {
    override fun projectOpened(project: Project) {
        if (!Settings.instance.runProjectSpecificPlugins) return

        val projectPath = project.basePath?.toFilePath() ?: return
        val pluginsPath = projectPath + livePluginsProjectDirName
        if (!pluginsPath.exists()) return

        val dataContext = MapDataContext(mapOf(CommonDataKeys.PROJECT.name to project))
        val dummyEvent = AnActionEvent(null, dataContext, "", Presentation(), ActionManager.getInstance(), 0)

        runPlugins(pluginsPath.listFiles(), dummyEvent)
    }

    override fun projectClosing(project: Project) {
        val projectPath = project.basePath?.toFilePath() ?: return
        val pluginsPath = projectPath + livePluginsProjectDirName
        if (!pluginsPath.exists()) return

        unloadPlugins(pluginsPath.listFiles())
    }
}

object LivePluginPaths {
    val ideJarsPath = PathManager.getHomePath().toFilePath() + "lib"

    val livePluginPath = PathManager.getPluginsPath().toFilePath() + "LivePlugin"
    val livePluginLibPath = PathManager.getPluginsPath().toFilePath() + "LivePlugin/lib"
    val livePluginsCompiledPath = PathManager.getPluginsPath().toFilePath() + "live-plugins-compiled"
    @JvmField val livePluginsPath = PathManager.getPluginsPath().toFilePath() + "live-plugins"
    val livePluginsProjectDirName = ".live-plugins"

    const val groovyExamplesPath = "groovy/"
    const val kotlinExamplesPath = "kotlin/"
}

class LivePluginAppComponent {
    companion object {
        const val livePluginId = "LivePlugin"
        val logger = Logger.getInstance(LivePluginAppComponent::class.java)
        val livePluginNotificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("Live Plugin")!!

        fun pluginIdToPathMap(): Map<String, FilePath> {
            // TODO Use virtual file because the code below will access file system every time this function is called to update availability of actions
            return livePluginsPath
                .listFiles { file -> file.isDirectory && file.name != DIRECTORY_STORE_FOLDER }
                .associateBy { it.name }
        }

        fun isInvalidPluginFolder(virtualFile: VirtualFile): Boolean =
            virtualFile.toFilePath().findAll(GroovyPluginRunner.mainScript).isEmpty() &&
            virtualFile.toFilePath().findAll(KotlinPluginRunner.mainScript).isEmpty()

        fun pluginExists(pluginId: String): Boolean = pluginId in pluginIdToPathMap().keys

        fun VirtualFile.pluginFolder(): VirtualFile? {
            val parent = parent ?: return null
            return if (parent.toFilePath() == livePluginsPath || parent.name == livePluginsProjectDirName) this
            else parent.pluginFolder()
        }

        // TODO similar to VirtualFile.pluginFolder
        fun FilePath.findPluginFolder(): FilePath? {
            val parent = toFile().parent?.toFilePath() ?: return null
            return if (parent == livePluginsPath || parent.name == livePluginsProjectDirName) this
            else parent.findPluginFolder()
        }

        fun readSampleScriptFile(filePath: String): String =
            try {
                val inputStream = LivePluginAppComponent::class.java.classLoader.getResourceAsStream(filePath) ?: error("Couldn't find resource for '$filePath'.")
                FileUtil.loadTextAndClose(inputStream)
            } catch (e: IOException) {
                logger.error(e)
                ""
            }

        fun runAllPlugins() {
            val actionManager = ActionManager.getInstance() // get ActionManager instance outside of EDT (because it failed in 201.6668.113)
            invokeLaterOnEDT {
                val event = AnActionEvent(
                    null,
                    EMPTY_CONTEXT,
                    ideStartupActionPlace,
                    Presentation(),
                    actionManager,
                    0
                )
                val pluginPaths = pluginIdToPathMap().keys.map { pluginIdToPathMap().getValue(it) }
                runPlugins(pluginPaths, event)
            }
        }
    }
}

class MakePluginFilesAlwaysEditable: NonProjectFileWritingAccessExtension {
    override fun isWritable(file: VirtualFile) = file.isUnderLivePluginsPath()
}

class EnableSyntaxHighlighterInLivePlugins: SyntaxHighlighterProvider {
    override fun create(fileType: FileType, project: Project?, file: VirtualFile?): SyntaxHighlighter? {
        if (project == null || file == null || !file.isUnderLivePluginsPath()) return null
        val language = LanguageUtil.getLanguageForPsi(project, file) ?: return null
        return SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, file)
    }
}

class UsageTypeExtension: UsageTypeProvider {
    override fun getUsageType(element: PsiElement): UsageType? {
        val file = PsiUtilCore.getVirtualFile(element) ?: return null
        return if (!file.isUnderLivePluginsPath()) null
        else UsageType { "Usage in liveplugin" }
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

