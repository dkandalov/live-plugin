package liveplugin.toolwindow;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileSystemTree;
import liveplugin.IDEUtil;

import static liveplugin.LivePluginAppComponent.clojureIsOnClassPath;

public class NewClojureFileAction extends NewFileAction {
	public NewClojureFileAction() {
		super("Clojure File", IDEUtil.clojureFileType);
	}

	@Override protected void update(FileSystemTree fileSystemTree, AnActionEvent e) {
		super.update(fileSystemTree, e);
		e.getPresentation().setVisible(clojureIsOnClassPath());
	}
}
