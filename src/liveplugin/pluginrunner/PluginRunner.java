package liveplugin.pluginrunner;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Function;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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


	class ClasspathAddition {
		private static final Logger LOG = Logger.getInstance(ClasspathAddition.class);

		public static List<String> findClasspathAdditions(String[] lines, String prefix, Map<String, String> environment, Function<String, Void> onError) throws IOException {
			List<String> pathsToAdd = new ArrayList<String>();
			for (String line : lines) {
				if (line.startsWith(prefix)) {
					String path = line.replace(prefix, "").trim();

					path = inlineEnvironmentVariables(path, environment);
					if (!new File(path).exists()) {
						onError.fun(path);
					} else {
						pathsToAdd.add(path);
					}
				}
			}
			return pathsToAdd;
		}

		private static String inlineEnvironmentVariables(String path, Map<String, String> environment) {
			boolean wasModified = false;
			for (Map.Entry<String, String> entry : environment.entrySet()) {
				path = path.replace("$" + entry.getKey(), entry.getValue());
				wasModified = true;
			}
			if (wasModified) LOG.info("Additional classpath with inlined env variables: " + path);
			return path;
		}
	}
}
