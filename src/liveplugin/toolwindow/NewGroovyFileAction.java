package liveplugin.toolwindow;

import liveplugin.IDEUtil;

public class NewGroovyFileAction extends NewFileAction {
	public NewGroovyFileAction() {
		super("Groovy File", NewElementPopupAction.inferIconFromFileType, IDEUtil.GROOVY_FILE_TYPE);
	}
}
