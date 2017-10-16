package liveplugin.toolwindow;

import liveplugin.IDEUtil;

public class NewGroovyFileAction extends NewFileAction {
	public NewGroovyFileAction() {
		super("Groovy File", IDEUtil.groovyFileType);
	}
}
