package liveplugin

import com.intellij.codeInsight.daemon.impl.analysis.DefaultHighlightingSettingProvider
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil.startsWith
import com.intellij.openapi.vfs.VirtualFile
import liveplugin.LivePluginAppComponent.livepluginsPath

class EnableHighlightingForLivePlugins: DefaultHighlightingSettingProvider(), DumbAware {
    override fun getDefaultSetting(project: Project, file: VirtualFile): FileHighlightingSetting? {
        return if (isUnderPluginsRootPath(file)) FileHighlightingSetting.FORCE_HIGHLIGHTING else null
    }

    private fun isUnderPluginsRootPath(file: VirtualFile): Boolean {
        return startsWith(file.path, livepluginsPath)
    }
}
