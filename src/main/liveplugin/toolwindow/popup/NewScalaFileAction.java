package liveplugin.toolwindow.popup;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileSystemTree;
import liveplugin.IDEUtil;

import static liveplugin.LivePluginAppComponent.scalaIsOnClassPath;

public class NewScalaFileAction extends NewFileAction {
	public NewScalaFileAction() {
		super("Scala File", IDEUtil.scalaFileType);
	}

	@Override protected void update(FileSystemTree fileSystemTree, AnActionEvent e) {
		super.update(fileSystemTree, e);
		e.getPresentation().setVisible(scalaIsOnClassPath());
	}
}
