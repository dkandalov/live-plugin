package liveplugin.toolwindow;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import liveplugin.IDEUtil;
import liveplugin.LivePluginAppComponent;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("ComponentNotRegistered")
public class RefreshPluginTreeAction extends AnAction implements DumbAware {

	public RefreshPluginTreeAction() {
		super("Refresh Plugin Tree", "Refresh Plugin Tree", IDEUtil.REFRESH_PLUGIN_LIST_ICON);
	}

	public static void refreshPluginTree() {
		ApplicationManager.getApplication().runWriteAction(new Runnable() {
			@Override public void run() {
				VirtualFile pluginsRoot = VirtualFileManager.getInstance().findFileByUrl("file://" + LivePluginAppComponent.pluginsRootPath());
				if (pluginsRoot == null) return;

				RefreshQueue.getInstance().refresh(false, true, new Runnable() {
					@Override public void run() {
						PluginToolWindowManager.reloadPluginTreesInAllProjects();
					}
				}, pluginsRoot);
			}
		});
	}

	@Override public void actionPerformed(@Nullable AnActionEvent e) {
		refreshPluginTree();
	}
}
