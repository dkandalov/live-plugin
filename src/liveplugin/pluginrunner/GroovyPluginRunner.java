/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package liveplugin.pluginrunner;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Function;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.util.GroovyScriptEngine;
import org.codehaus.groovy.control.CompilationFailedException;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static liveplugin.MyFileUtil.*;

public class GroovyPluginRunner implements PluginRunner {
	public static final String MAIN_SCRIPT = "plugin.groovy";
	private static final String GROOVY_ADD_TO_CLASSPATH_KEYWORD = "// " + ADD_TO_CLASSPATH_KEYWORD;
	private static final Logger LOG = Logger.getInstance(GroovyPluginRunner.class);

	private final ErrorReporter errorReporter;
	private final Map<String,String> environment;

	public GroovyPluginRunner(ErrorReporter errorReporter, Map<String, String> environment) {
		this.errorReporter = errorReporter;
		this.environment = new HashMap<String, String>(environment);
	}

	@Override public boolean canRunPlugin(String pathToPluginFolder) {
		return findSingleFileIn(pathToPluginFolder, MAIN_SCRIPT) != null;
	}

	@Override public void runPlugin(String pathToPluginFolder, String pluginId, Map<String, ?> binding,
	                                Function<Runnable, Void> runOnEDTCallback) {
		File mainScript = findSingleFileIn(pathToPluginFolder, MAIN_SCRIPT);
		String pluginFolderUrl = "file:///" + pathToPluginFolder;
		runGroovyScript(asUrl(mainScript), pluginFolderUrl, pluginId, binding, runOnEDTCallback);
	}

	private void runGroovyScript(final String mainScriptUrl, String pluginFolderUrl, final String pluginId,
	                             final Map<String, ?> binding, Function<Runnable, Void> runPluginCallback) {
		try {
			environment.put("THIS_SCRIPT", mainScriptUrl);

			List<String> pathsToAdd = findClasspathAdditions(readLines(mainScriptUrl), GROOVY_ADD_TO_CLASSPATH_KEYWORD, environment, new Function<String, Void>() {
				@Override public Void fun(String path) {
					errorReporter.addLoadingError(pluginId, "Couldn't find dependency '" + path + "'");
					return null;
				}
			});
			pathsToAdd.add(pluginFolderUrl);
			GroovyClassLoader classLoader = createClassLoaderWithDependencies(pathsToAdd, mainScriptUrl, pluginId);

			// assume that GroovyScriptEngine is thread-safe
			final GroovyScriptEngine scriptEngine = new GroovyScriptEngine(pluginFolderUrl, classLoader);
			try {
				scriptEngine.loadScriptByName(mainScriptUrl);
			} catch (Exception e) {
				errorReporter.addRunningError(pluginId, e);
				return;
			}

			runPluginCallback.fun(new Runnable() {
				@Override public void run() {
					try {
						scriptEngine.run(mainScriptUrl, createGroovyBinding(binding));
					} catch (Exception e) {
						errorReporter.addRunningError(pluginId, e);
					}
				}
			});

		} catch (IOException e) {
			errorReporter.addLoadingError(pluginId, "Error while creating scripting engine. " + e.getMessage());
		} catch (CompilationFailedException e) {
			errorReporter.addLoadingError(pluginId, "Error while compiling script. " + e.getMessage());
		} catch (VerifyError e) {
			errorReporter.addLoadingError(pluginId, "Error while compiling script. " + e.getMessage());
		}
	}

	private static Binding createGroovyBinding(Map<String, ?> binding) {
		Binding result = new Binding();
		for (Map.Entry<String, ?> entry : binding.entrySet()) {
			result.setVariable(entry.getKey(), entry.getValue());
		}
		return result;
	}

	private GroovyClassLoader createClassLoaderWithDependencies(List<String> pathsToAdd, String mainScriptUrl, String pluginId) {
		GroovyClassLoader classLoader = new GroovyClassLoader(this.getClass().getClassLoader());
		try {

			for (String path : pathsToAdd) {
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
			errorReporter.addLoadingError(pluginId, "Error while looking for dependencies. Main script: " + mainScriptUrl + ". " + e.getMessage());
		}
		return classLoader;
	}

	static List<String> findClasspathAdditions(String[] lines, String prefix, Map<String, String> environment, Function<String, Void> onError) throws IOException {
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
		if (wasModified) LOG.info("Path with env variables: " + path);
		return path;
	}
}
