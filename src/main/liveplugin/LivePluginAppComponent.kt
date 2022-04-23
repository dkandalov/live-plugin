package liveplugin

import com.intellij.ide.scratch.RootType
import com.intellij.lang.LanguageUtil
import com.intellij.notification.NotificationGroupManager
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
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UseScopeEnlarger
import com.intellij.psi.util.PsiUtilCore
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProvider
import com.intellij.util.indexing.IndexableSetContributor
import liveplugin.LivePluginAppComponent.Companion.isPluginFolder
import liveplugin.LivePluginPaths.livePluginsPath
import liveplugin.LivePluginPaths.livePluginsProjectDirName
import liveplugin.common.FilePath
import liveplugin.common.Icons.pluginIcon
import liveplugin.common.toFilePath

class LivePluginAppComponent {
    companion object {
        const val livePluginId = "LivePlugin"

        // Lazy because it seems that it can be initialised before notification group is initialised in plugin.xml
        val livePluginNotificationGroup by lazy {
            NotificationGroupManager.getInstance().getNotificationGroup("Live Plugin")!!
        }

        fun pluginIdToPathMap(): Map<String, FilePath> =
            livePluginsPath.toVirtualFile()!!
                .children.filter { file -> file.isDirectory && file.name != DIRECTORY_STORE_FOLDER }
                .map { it.toFilePath() }
                .associateBy { it.name }

        fun VirtualFile.findPluginFolder(): VirtualFile? =
            if (parent == null) null
            else if (isPluginFolder()) this
            else parent.findPluginFolder()

        fun VirtualFile.isPluginFolder() = parent.toFilePath() == livePluginsPath || parent.name == livePluginsProjectDirName

        // TODO similar to VirtualFile.pluginFolder
        fun FilePath.findPluginFolder(): FilePath? {
            val parent = toFile().parent?.toFilePath() ?: return null
            return if (parent == livePluginsPath || parent.name == livePluginsProjectDirName) this
            else parent.findPluginFolder()
        }
    }
}

// For consistency with "IDE Consoles" it's good to have live plugins under "Scratches and Consoles"
// but it's also used for enabling Kotlin intentions in live plugin, i.e. outside of project
// (since change in IJ 2022.1: Anna Kozlova* 22/12/2021, 17:21 [kotlin] disable intentions which modifies code in libraries (KTIJ-20543))
class ScratchLivePluginRootType : RootType("LivePlugin", "Live Plugins") {
    override fun substituteIcon(project: Project, file: VirtualFile) =
        if (file.isPluginFolder()) pluginIcon else super.substituteIcon(project, file)

    companion object {
        init {
            System.setProperty(PathManager.PROPERTY_SCRATCH_PATH + "/LivePlugin", livePluginsPath.value)
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
