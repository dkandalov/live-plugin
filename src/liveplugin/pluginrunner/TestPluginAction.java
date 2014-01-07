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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import liveplugin.IdeUtil;
import liveplugin.LivePluginAppComponent;
import liveplugin.Settings;

import java.util.*;

import static com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT;
import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.util.containers.ContainerUtil.find;
import static com.intellij.util.containers.ContainerUtil.map;
import static liveplugin.IdeUtil.SingleThreadBackgroundRunner;
import static liveplugin.LivePluginAppComponent.checkThatGroovyIsOnClasspath;
import static liveplugin.pluginrunner.GroovyPluginRunner.TEST_SCRIPT;
import static liveplugin.pluginrunner.PluginRunner.IDE_STARTUP;

public class TestPluginAction extends AnAction {
	private static final SingleThreadBackgroundRunner backgroundRunner = new SingleThreadBackgroundRunner("TestLivePlugin thread");
	private static final Function<Runnable,Void> RUN_ON_EDT = new Function<Runnable, Void>() {
		@Override public Void fun(Runnable runnable) {
			UIUtil.invokeAndWaitIfNeeded(runnable);
			return null;
		}
	};

	public TestPluginAction() {
		super("Test Plugin", "Test selected plugins", IdeUtil.TEST_PLUGIN_ICON);
	}

	@Override public void actionPerformed(AnActionEvent event) {
		testCurrentPlugin(event);
	}

	@Override public void update(AnActionEvent event) {
		event.getPresentation().setEnabled(!RunPluginAction.findCurrentPluginIds(event).isEmpty());
	}

	private void testCurrentPlugin(AnActionEvent event) {
		IdeUtil.saveAllFiles();
		List<String> pluginIds = RunPluginAction.findCurrentPluginIds(event);
		runPlugins(pluginIds, event);
	}

	// TODO refactor to avoid duplication with RunPluginAction
	public static void runPlugins(final Collection<String> pluginIds, AnActionEvent event) {
		checkThatGroovyIsOnClasspath();

		final Project project = event.getProject();
		final boolean isIdeStartup = event.getPlace().equals(IDE_STARTUP);

		if (!isIdeStartup) {
			Settings.countPluginsUsage(pluginIds);
		}

		Runnable runPlugins = new Runnable() {
			@Override public void run() {
				ErrorReporter errorReporter = new ErrorReporter();
				List<PluginRunner> pluginRunners = createPluginRunners(errorReporter);

				for (final String pluginId : pluginIds) {
					final String pathToPluginFolder = LivePluginAppComponent.pluginIdToPathMap().get(pluginId); // TODO not thread-safe
					final PluginRunner pluginRunner = find(pluginRunners, new Condition<PluginRunner>() {
						@Override public boolean value(PluginRunner it) {
							return it.canRunPlugin(pathToPluginFolder);
						}
					});
					if (pluginRunner == null) {
						String runners = join(map(pluginRunners, new Function<PluginRunner, Object>() {
							@Override public Object fun(PluginRunner it) {
								return it.scriptName();
							}
						}), ", ");
						errorReporter.addLoadingError(pluginId, "Test script was not found. Tried: " + runners + ".");
						errorReporter.reportAllErrors(new ErrorReporter.Callback() {
							@Override public void display(String title, String message) {
								IdeUtil.displayInConsole(title, message, ERROR_OUTPUT, project);
							}
						});
						continue;
					}

					final Map<String, Object> binding = createBinding(pathToPluginFolder, project, isIdeStartup);
					pluginRunner.runPlugin(pathToPluginFolder, pluginId, binding, RUN_ON_EDT);

					errorReporter.reportAllErrors(new ErrorReporter.Callback() {
						@Override public void display(String title, String message) {
							IdeUtil.displayInConsole(title, message, ERROR_OUTPUT, project);
						}
					});
				}
			}
		};

		backgroundRunner.run(project, "Loading plugin", runPlugins);
	}

	private static List<PluginRunner> createPluginRunners(ErrorReporter errorReporter) {
		List<PluginRunner> result = new ArrayList<PluginRunner>();
		result.add(new GroovyPluginRunner(TEST_SCRIPT, errorReporter, environment()));
		return result;
	}

	private static Map<String, Object> createBinding(String pathToPluginFolder, Project project, boolean isIdeStartup) {
		Map<String, Object> binding = new HashMap<String, Object>();
		binding.put("project", project);
		binding.put("isIdeStartup", isIdeStartup);
		binding.put("pluginPath", pathToPluginFolder);
		return binding;
	}

	private static Map<String, String> environment() {
		Map<String, String> result = new HashMap<String, String>(System.getenv());
		result.put("INTELLIJ_PLUGINS_PATH", PathManager.getPluginsPath());
		result.put("INTELLIJ_LIBS", PathManager.getLibPath());
		return result;
	}
}
