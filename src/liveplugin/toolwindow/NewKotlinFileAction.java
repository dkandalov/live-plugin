package liveplugin.toolwindow;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.fileChooser.FileSystemTree;
import liveplugin.IDEUtil;

public class NewKotlinFileAction extends NewFileAction {
	public NewKotlinFileAction() {
		super("Kotlin File", IDEUtil.kotlinFileType);
	}

	@Override protected void update(FileSystemTree fileSystemTree, AnActionEvent e) {
		super.update(fileSystemTree, e);
	}
}
