package liveplugin;

import com.intellij.openapi.actionSystem.AnActionEvent;

public interface Evaluator {
	String IDE_STARTUP = "IDE_STARTUP";
	String CLASSPATH_PREFIX = "// add-to-classpath ";

	void doEval(String pluginId, String pathToPluginFolder, AnActionEvent event);
}
