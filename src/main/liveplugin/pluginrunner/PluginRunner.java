package liveplugin.pluginrunner;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.util.Function;
import groovy.lang.GroovyClassLoader;
import org.apache.commons.httpclient.util.URIUtil;
import org.apache.oro.io.GlobFilenameFilter;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.util.containers.ContainerUtil.map;

public interface PluginRunner {
	String ideStartup = "IDE_STARTUP";
	String addToClasspathKeyword = "add-to-classpath ";
	String dependsOnPluginKeyword = "depends-on-plugin ";

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

	String scriptName();


	class ClasspathAddition {
		private static final Logger logger = Logger.getInstance(ClasspathAddition.class);

		public static ClassLoader createClassLoaderWithDependencies(List<String> pathsToAdd, List<String> pluginsToAdd,
		                                                            String mainScriptUrl, String pluginId, ErrorReporter errorReporter) {
			ClassLoader parentLoader = createParentClassLoader(pluginsToAdd, pluginId, errorReporter);
			GroovyClassLoader classLoader = new GroovyClassLoader(parentLoader);
			try {

				for (String path : pathsToAdd) {
					path = URIUtil.encodePath(path);
					if (path.startsWith("file:/")) {
						URL url = new URL(path);
						classLoader.addURL(url);
						classLoader.addClasspath(url.getFile());
					} else {
						classLoader.addURL(new URL("file:///" + path));
						classLoader.addClasspath(path);
					}
				}

			} catch (IOException e) {
				errorReporter.addLoadingError(pluginId, "Error while looking for dependencies in '" + mainScriptUrl + "'. " + e.getMessage());
			}
			return classLoader;
		}

		public static ClassLoader createParentClassLoader(List<String> dependentPlugins, final String pluginId, final ErrorReporter errorReporter) {
			List<IdeaPluginDescriptor> pluginDescriptors = pluginDescriptorsOf(dependentPlugins, dependentPluginId -> {
				errorReporter.addLoadingError(pluginId, "Couldn't find dependent plugin '" + dependentPluginId + "'");
				return null;
			});
			List<ClassLoader> parentLoaders = new ArrayList<>(map(pluginDescriptors, it -> it.getPluginClassLoader()));
			parentLoaders.add(PluginRunner.class.getClassLoader());

			String pluginVersion = "1.0.0";
			return new PluginClassLoader(
					new ArrayList<>(),
					parentLoaders.toArray(new ClassLoader[parentLoaders.size()]),
					PluginId.getId(pluginId),
					pluginVersion,
					null
			);
		}

		public static List<IdeaPluginDescriptor> pluginDescriptorsOf(List<String> pluginIds, Function<String, Void> onError) {
			List<IdeaPluginDescriptor> result = new ArrayList<>();
			for (String pluginIdString : pluginIds) {
				IdeaPluginDescriptor pluginDescriptor = PluginManager.getPlugin(PluginId.getId(pluginIdString));
				if (pluginDescriptor == null) {
					onError.fun(pluginIdString);
				} else {
					result.add(pluginDescriptor);
				}
			}
			return result;
		}

		public static List<String> findPluginDependencies(String[] lines, String prefix) {
			List<String> pluginsToAdd = new ArrayList<>();
			for (String line : lines) {
				if (!line.startsWith(prefix)) continue;

				String pluginId = line.replace(prefix, "").trim();
				pluginsToAdd.add(pluginId);
			}
			return pluginsToAdd;
		}

		public static List<String> findClasspathAdditions(String[] lines, String prefix, Map<String, String> environment, Function<String, Void> onError) throws IOException {
			List<String> pathsToAdd = new ArrayList<>();
			for (String line : lines) {
				if (!line.startsWith(prefix)) continue;

				String path = line.replace(prefix, "").trim();
				path = inlineEnvironmentVariables(path, environment);

				List<String> matchingFiles = findMatchingFiles(path);
				if (matchingFiles.isEmpty()) {
					onError.fun(path);
				} else {
					pathsToAdd.addAll(matchingFiles);
				}
			}
			return pathsToAdd;
		}

		private static List<String> findMatchingFiles(String pathAndPattern) {
			if (new File(pathAndPattern).exists()) return Collections.singletonList(pathAndPattern);

			int separatorIndex = pathAndPattern.lastIndexOf(File.separator);
			String path = pathAndPattern.substring(0, separatorIndex + 1);
			String pattern = pathAndPattern.substring(separatorIndex + 1);

			File[] files = new File(path).listFiles((FileFilter) new GlobFilenameFilter(pattern));
			files = (files == null ? new File[0] : files);
			return map(files, File::getAbsolutePath);
		}

		private static String inlineEnvironmentVariables(String path, Map<String, String> environment) {
			boolean wasModified = false;
			for (Map.Entry<String, String> entry : environment.entrySet()) {
				path = path.replace("$" + entry.getKey(), entry.getValue());
				wasModified = true;
			}
			if (wasModified) logger.info("Additional classpath with inlined env variables: " + path);
			return path;
		}
	}
}
