package liveplugin

import com.intellij.codeInsight.daemon.impl.analysis.DefaultHighlightingSettingProvider
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting.FORCE_HIGHLIGHTING
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil.startsWith
import com.intellij.openapi.vfs.VirtualFile
import liveplugin.LivePluginAppComponent.Companion.livepluginsPath

class EnableHighlightingForLivePlugins: DefaultHighlightingSettingProvider(), DumbAware {
    override fun getDefaultSetting(project: Project, file: VirtualFile): FileHighlightingSetting? {
        val isUnderPluginsRootPath = startsWith(file.path, livepluginsPath)
        return if (isUnderPluginsRootPath) FORCE_HIGHLIGHTING else null
    }
}
