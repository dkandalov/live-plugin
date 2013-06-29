package liveplugin.pluginrunner;

import com.intellij.openapi.actionSystem.AnActionEvent;

public interface PluginRunner {
	String IDE_STARTUP = "IDE_STARTUP";
	String CLASSPATH_PREFIX = "// add-to-classpath ";

	boolean canRunPlugin(String pathToPluginFolder);

	void runPlugin(String pathToPluginFolder, String pluginId, AnActionEvent event);
}
