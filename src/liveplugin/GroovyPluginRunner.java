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
package liveplugin;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.util.GroovyScriptEngine;
import org.codehaus.groovy.control.CompilationFailedException;

import java.io.*;
import java.net.URL;
import java.util.*;

class GroovyPluginRunner implements PluginRunner {
	private static final Logger LOG = Logger.getInstance(GroovyPluginRunner.class);

	private final ErrorReporter errorReporter;

	public GroovyPluginRunner(ErrorReporter errorReporter) {
		this.errorReporter = errorReporter;
	}

	@Override public void runPlugin(String pluginId, String pathToPluginFolder, AnActionEvent event) {

		String mainScriptPath = findMainScriptIn(pathToPluginFolder);
		if (mainScriptPath == null) {
			errorReporter.addLoadingError(pluginId, "Couldn't find " + LivePluginAppComponent.MAIN_SCRIPT);
			return;
		}

		try {

			GroovyClassLoader classLoader = createClassLoaderWithDependencies(mainScriptPath, pluginId, pluginEnvironment(mainScriptPath));
			GroovyScriptEngine scriptEngine = new GroovyScriptEngine("file:///" + pathToPluginFolder, classLoader);
			Binding binding = new Binding();

			binding.setVariable("event", event);
			binding.setVariable("project", event.getProject());
			binding.setVariable("isIdeStartup", event.getPlace().equals(IDE_STARTUP));
			binding.setVariable("pluginPath", pathToPluginFolder);

			scriptEngine.run("file:///" + mainScriptPath, binding);

		} catch (IOException e) {
			errorReporter.addLoadingError(pluginId, "Error while creating scripting engine. " + e.getMessage());
		} catch (CompilationFailedException e) {
			errorReporter.addLoadingError(pluginId, "Error while compiling script. " + e.getMessage());
		} catch (VerifyError e) {
			errorReporter.addLoadingError(pluginId, "Error while compiling script. " + e.getMessage());
		} catch (Exception e) {
			errorReporter.addRunningPluginException(pluginId, e);
		}
	}

	private static Map<String, String> pluginEnvironment(String mainScriptPath) {
		Map<String, String> result = new HashMap<String, String>(System.getenv());
		result.put("THIS_SCRIPT", mainScriptPath);
		result.put("INTELLIJ_PLUGINS_PATH", PathManager.getPluginsPath());
		result.put("INTELLIJ_LIBS", PathManager.getLibPath());
		return result;
	}

	private String findMainScriptIn(String path) {
		List<File> files = allFilesInDirectory(new File(path));
		List<File> result = new ArrayList<File>();
		for (File file : files) {
			if (LivePluginAppComponent.MAIN_SCRIPT.equals(file.getName())) {
				result.add(file);
			}
		}
		if (result.size() == 0) return null;
		else if (result.size() == 1) return result.get(0).getAbsolutePath();
		else throw new IllegalStateException("Found several " + LivePluginAppComponent.MAIN_SCRIPT + " files under " + path);
	}

	private GroovyClassLoader createClassLoaderWithDependencies(String mainScriptPath, String pluginId, Map<String, String> environment) {
		GroovyClassLoader classLoader = new GroovyClassLoader(this.getClass().getClassLoader());

		try {
			classLoader.addURL(new URL("file:///" + new File(mainScriptPath).getParent()));
			classLoader.addClasspath(new File(mainScriptPath).getParent());

			BufferedReader inputStream = new BufferedReader(new InputStreamReader(new FileInputStream(mainScriptPath)));
			String line;
			while ((line = inputStream.readLine()) != null) {
				if (line.startsWith(CLASSPATH_PREFIX)) {
					String path = line.replace(CLASSPATH_PREFIX, "").trim();

					path = inlineEnvironmentVariables(path, environment);
					if (!new File(path).exists()) {
						errorReporter.addLoadingError(pluginId, "Couldn't find dependency '" + path + "'");
					} else {
						classLoader.addURL(new URL("file:///" + path));
						classLoader.addClasspath(path);
					}
				}
			}
		} catch (IOException e) {
			errorReporter.addLoadingError(pluginId, "Error while looking for dependencies. Main script: " + mainScriptPath + ". " + e.getMessage());
		}
		return classLoader;
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

	private static List<File> allFilesInDirectory(File dir) {
		LinkedList<File> result = new LinkedList<File>();
		File[] files = dir.listFiles();
		if (files == null) return result;

		for (File file : files) {
			if (file.isFile()) {
				result.add(file);
			} else if (file.isDirectory()) {
				result.addAll(allFilesInDirectory(file));
			}
		}
		return result;
	}

}
