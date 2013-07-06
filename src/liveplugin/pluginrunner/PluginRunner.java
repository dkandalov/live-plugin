package liveplugin.pluginrunner;

import com.intellij.util.Function;

import java.util.Map;

public interface PluginRunner {
	String IDE_STARTUP = "IDE_STARTUP";
	String ADD_TO_CLASSPATH_KEYWORD = "add-to-classpath ";

	/**
	 * @param pathToPluginFolder absolute path to plugin folder
	 * @return true if {@link PluginRunner} can run plugin in this folder
	 */
	boolean canRunPlugin(String pathToPluginFolder);

	/**
	 * @param pathToPluginFolder absolute path to plugin folder
	 * @param pluginId plugin id, e.g. to distinguish it from other plugins in error messages
	 * @param binding map with implicit variables available in plugin script
	 * @param runOnEDTCallback callback which should be used to run plugin code on EDT
	 */
	void runPlugin(String pathToPluginFolder, String pluginId, Map<String, ?> binding, Function<Runnable, Void> runOnEDTCallback);
}
