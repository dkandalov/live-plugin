package liveplugin.toolwindow;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import liveplugin.IdeUtil;
import liveplugin.LivePluginAppComponent;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("ComponentNotRegistered")
public class RefreshPluginTreeAction extends AnAction {

	public RefreshPluginTreeAction() {
		super("Refresh Plugin Tree", "Refresh Plugin Tree", IdeUtil.REFRESH_PLUGIN_LIST_ICON);
	}

	@Override public void actionPerformed(@Nullable AnActionEvent e) {
		refreshPluginTree();
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
}
