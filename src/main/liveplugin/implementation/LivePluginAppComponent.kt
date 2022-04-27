package liveplugin.implementation

import com.intellij.ide.scratch.RootType
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.application.PathManager
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
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UseScopeEnlarger
import com.intellij.psi.util.PsiUtilCore
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider
import com.intellij.util.indexing.IndexableSetContributor
import liveplugin.implementation.LivePluginAppComponent.Companion.isPluginFolder
import liveplugin.implementation.LivePluginPaths.livePluginsCompiledPath
import liveplugin.implementation.LivePluginPaths.livePluginsPath
import liveplugin.implementation.LivePluginPaths.livePluginsProjectDirName
import liveplugin.implementation.common.FilePath
import liveplugin.implementation.common.Icons.pluginIcon
import liveplugin.implementation.common.IdeUtil.runLaterOnEDT
import liveplugin.implementation.common.toFilePath
import liveplugin.implementation.pluginrunner.UnloadPluginAction
import liveplugin.implementation.toolwindow.util.delete

class LivePluginAppComponent {
    companion object {
        @JvmStatic fun livePluginsById(): Map<String, LivePlugin> =
            livePluginsPath.listFiles { file -> file.isDirectory && file.name != DIRECTORY_STORE_FOLDER }
                .map { LivePlugin(it) }
                .associateBy { it.id }

        fun FilePath.isPluginFolder(): Boolean {
            if (!isDirectory && exists()) return false
            val parentPath = parent ?: return false
            return parentPath == livePluginsPath || parentPath.name == livePluginsProjectDirName
        }
    }
}

// For consistency with "IDE Consoles" it's good to have live plugins under "Scratches and Consoles"
// but it's also used for enabling Kotlin intentions in live plugin, i.e. outside of project
// (since change in IJ 2022.1: Anna Kozlova* 22/12/2021, 17:21 [kotlin] disable intentions which modifies code in libraries (KTIJ-20543))
class ScratchLivePluginRootType : RootType("LivePlugin", "Live Plugins") {
    override fun substituteIcon(project: Project, file: VirtualFile) =
        if (file.toFilePath().isPluginFolder()) pluginIcon else super.substituteIcon(project, file)

    companion object {
        init {
            System.setProperty(PathManager.PROPERTY_SCRATCH_PATH + "/LivePlugin", livePluginsPath.value)
        }
    }
}

class LivePluginDeletedListener : BulkFileListener {
    override fun before(events: List<VFileEvent>) {
        val livePlugins = events.filter { it is VFileDeleteEvent && it.file.toFilePath().isPluginFolder() }
            .mapNotNull { event -> event.file?.toFilePath() }
            .toLivePlugins()

        if (livePlugins.isNotEmpty()) {
            runLaterOnEDT {
                UnloadPluginAction.unloadPlugins(livePlugins)
                livePlugins.forEach { plugin ->
                    (livePluginsCompiledPath + plugin.id).toVirtualFile()?.delete()
                }
            }
        }
    }
}

class MakePluginFilesAlwaysEditable : NonProjectFileWritingAccessExtension {
    override fun isWritable(file: VirtualFile) = file.isUnderLivePluginsPath()
}

class EnableSyntaxHighlighterInLivePlugins : SyntaxHighlighterProvider {
    override fun create(fileType: FileType, project: Project?, file: VirtualFile?): SyntaxHighlighter? {
        if (project == null || file == null || !file.isUnderLivePluginsPath()) return null
        val language = LanguageUtil.getLanguageForPsi(project, file) ?: return null
        return SyntaxHighlighterFactory.getSyntaxHighlighter(language, project, file)
    }
}

class UsageTypeExtension : UsageTypeProvider {
    override fun getUsageType(element: PsiElement): UsageType? {
        val file = PsiUtilCore.getVirtualFile(element) ?: return null
        return if (!file.isUnderLivePluginsPath()) null
        else UsageType { "Usage in liveplugin" }
    }
}

class IndexSetContributor : IndexableSetContributor() {
    override fun getAdditionalRootsToIndex(): Set<VirtualFile> {
        return mutableSetOf(livePluginsPath.toVirtualFile() ?: return HashSet())
    }
}

class UseScopeExtension : UseScopeEnlarger() {
    override fun getAdditionalUseScope(element: PsiElement): SearchScope? {
        val useScope = element.useScope
        return if (useScope is LocalSearchScope) null else LivePluginsSearchScope.getScopeInstance(element.project)
    }

    private class LivePluginsSearchScope(project: Project) : GlobalSearchScope(project) {
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
