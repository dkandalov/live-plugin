package liveplugin;

import com.intellij.codeInsight.daemon.impl.analysis.DefaultHighlightingSettingProvider;
import com.intellij.codeInsight.daemon.impl.analysis.FileHighlightingSetting;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EnableHighlightingForLivePlugins extends DefaultHighlightingSettingProvider implements DumbAware {
	@Nullable @Override public FileHighlightingSetting getDefaultSetting(@NotNull Project project, @NotNull VirtualFile file) {
		return isUnderPluginsRootPath(file) ? FileHighlightingSetting.FORCE_HIGHLIGHTING : null;
	}

	private static boolean isUnderPluginsRootPath(@NotNull VirtualFile file) {
		return FileUtil.startsWith(file.getPath(), LivePluginAppComponent.pluginsRootPath());
	}
}
