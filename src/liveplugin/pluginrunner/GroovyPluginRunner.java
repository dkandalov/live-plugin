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

import com.intellij.util.Function;
import groovy.lang.Binding;
import groovy.util.GroovyScriptEngine;
import org.codehaus.groovy.control.CompilationFailedException;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static liveplugin.MyFileUtil.*;
import static liveplugin.pluginrunner.PluginRunner.ClasspathAddition.*;

public class GroovyPluginRunner implements PluginRunner {
	public static final String MAIN_SCRIPT = "plugin.groovy";
	public static final String TEST_SCRIPT = "plugin-test.groovy";
	public static final String GROOVY_ADD_TO_CLASSPATH_KEYWORD = "// " + ADD_TO_CLASSPATH_KEYWORD;
	public static final String GROOVY_DEPENDS_ON_PLUGIN_KEYWORD = "// " + DEPENDS_ON_PLUGIN_KEYWORD;

	private final String scriptName;
	private final ErrorReporter errorReporter;
	private final Map<String, String> environment;

	public GroovyPluginRunner(String scriptName, ErrorReporter errorReporter, Map<String, String> environment) {
		this.scriptName = scriptName;
		this.errorReporter = errorReporter;
		this.environment = new HashMap<String, String>(environment);
	}

	@Override public boolean canRunPlugin(String pathToPluginFolder) {
		return findScriptFileIn(pathToPluginFolder, scriptName) != null;
	}

	@Override public void runPlugin(String pathToPluginFolder, String pluginId, Map<String, ?> binding,
	                                Function<Runnable, Void> runOnEDTCallback) {
		File mainScript = findScriptFileIn(pathToPluginFolder, scriptName);
		runGroovyScript(asUrl(mainScript), pathToPluginFolder, pluginId, binding, runOnEDTCallback);
	}

	@Override public String scriptName() {
		return scriptName;
	}

	private void runGroovyScript(final String mainScriptUrl, String pathToPluginFolder, final String pluginId,
	                             final Map<String, ?> binding, Function<Runnable, Void> runPluginCallback) {
		try {
			environment.put("PLUGIN_PATH", pathToPluginFolder);

			List<String> dependentPlugins = findPluginDependencies(readLines(mainScriptUrl), GROOVY_DEPENDS_ON_PLUGIN_KEYWORD);
			List<String> pathsToAdd = findClasspathAdditions(readLines(mainScriptUrl), GROOVY_ADD_TO_CLASSPATH_KEYWORD, environment, new Function<String, Void>() {
				@Override public Void fun(String path) {
					errorReporter.addLoadingError(pluginId, "Couldn't find dependency '" + path + "'");
					return null;
				}
			});
			String pluginFolderUrl = "file:///" + pathToPluginFolder + "/"; // prefix with "file:///" so that unix-like path works on windows
			pathsToAdd.add(pluginFolderUrl);
			ClassLoader classLoader = createClassLoaderWithDependencies(pathsToAdd, dependentPlugins, mainScriptUrl, pluginId, errorReporter);

			// assume that GroovyScriptEngine is thread-safe
			// (according to this http://groovy.329449.n5.nabble.com/Is-the-GroovyScriptEngine-thread-safe-td331407.html)
			final GroovyScriptEngine scriptEngine = new GroovyScriptEngine(pluginFolderUrl, classLoader);
			try {
				scriptEngine.loadScriptByName(mainScriptUrl);
			} catch (Exception e) {
				errorReporter.addLoadingError(pluginId, e);
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
			errorReporter.addLoadingError(pluginId, "Error creating scripting engine. " + e.getMessage());
		} catch (CompilationFailedException e) {
			errorReporter.addLoadingError(pluginId, "Error compiling script. " + e.getMessage());
		} catch (LinkageError e) {
			errorReporter.addLoadingError(pluginId, "Error linking script. " + e.getMessage());
		} catch (Error e) {
			errorReporter.addLoadingError(pluginId, e);
		} catch (Exception e) {
			errorReporter.addLoadingError(pluginId, e);
		}
	}

    private static Binding createGroovyBinding(Map<String, ?> binding) {
        Binding result = new Binding();
        for (Map.Entry<String, ?> entry : binding.entrySet()) {
            result.setVariable(entry.getKey(), entry.getValue());
        }
        return result;
    }
}
