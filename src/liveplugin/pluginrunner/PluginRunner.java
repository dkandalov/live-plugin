package liveplugin.pluginrunner;

import com.intellij.util.Function;

import java.util.Map;

public interface PluginRunner {
	String IDE_STARTUP = "IDE_STARTUP";
	String CLASSPATH_PREFIX = "// add-to-classpath ";

	boolean canRunPlugin(String pathToPluginFolder);

	void runPlugin(String pathToPluginFolder, String pluginId, Map<String, ?> binding, Function<Runnable, Void> runPluginCallback);
}
