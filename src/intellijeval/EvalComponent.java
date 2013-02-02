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
package intellijeval;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import intellijeval.toolwindow.PluginToolWindowManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER;
import static com.intellij.openapi.util.io.FileUtilRt.toSystemIndependentName;
import static intellijeval.toolwindow.PluginToolWindowManager.ExamplePluginInstaller;
import static java.util.Arrays.asList;

/**
 * @author DKandalov
 */
public class EvalComponent implements ApplicationComponent { // TODO implement DumbAware?
	private static final Logger LOG = Logger.getInstance(EvalComponent.class);

	public static final String MAIN_SCRIPT = "plugin.groovy";
	public static final String PLUGIN_EXAMPLES_PATH = "/intellijeval/pluginexamples";

	private static final String DEFAULT_PLUGIN_PATH = PLUGIN_EXAMPLES_PATH;
	private static final String DEFAULT_PLUGIN_SCRIPT = "default-plugin.groovy";

	private static final String DEFAULT_IDEA_OUTPUT_FOLDER = "out";

	public static String pluginsRootPath() {
		return toSystemIndependentName(PathManager.getPluginsPath() + "/intellij-eval-plugins");
	}

	public static Map<String, String> pluginIdToPathMap() {
		final boolean containsIdeaProjectFolder = new File(pluginsRootPath() + "/" + DIRECTORY_STORE_FOLDER).exists();

		File[] files = new File(pluginsRootPath()).listFiles(new FileFilter() {
			@SuppressWarnings("SimplifiableIfStatement")
			@Override public boolean accept(File file) {
				if (containsIdeaProjectFolder && file.getName().equals(DEFAULT_IDEA_OUTPUT_FOLDER)) return false;
				if (file.getName().equals(DIRECTORY_STORE_FOLDER)) return false;
				return file.isDirectory();
			}
		});
		if (files == null) return new HashMap<String, String>();

		HashMap<String, String> result = new HashMap<String, String>();
		for (File file : files) {
			result.put(file.getName(), toSystemIndependentName(file.getAbsolutePath()));
		}
		return result;
	}

	public static boolean isInvalidPluginFolder(VirtualFile virtualFile) {
		File file = new File(virtualFile.getPath());
		if (!file.isDirectory()) return false;
		String[] files = file.list(new FilenameFilter() {
			@Override public boolean accept(File dir, String name) {
				return name.equals(MAIN_SCRIPT);
			}
		});
		return files.length < 1;
	}

	public static String defaultPluginScript() {
		return readSampleScriptFile(DEFAULT_PLUGIN_PATH, DEFAULT_PLUGIN_SCRIPT);
	}

	public static String readSampleScriptFile(String pluginPath, String file) {
		try {
			String path = pluginPath + "/" + file;
			return FileUtil.loadTextAndClose(EvalComponent.class.getClassLoader().getResourceAsStream(path));
		} catch (IOException e) {
			LOG.error(e);
			return "";
		}
	}

	public static boolean pluginExists(String pluginId) {
		return pluginIdToPathMap().keySet().contains(pluginId);
	}

	@Override public void initComponent() {
		Settings settings = Settings.getInstance();
		if (settings.justInstalled) {
			installHelloWorldPlugin();
			settings.justInstalled = false;
		}
		if (settings.runAllPluginsOnIDEStartup) {
			runAllPlugins();
		}

		new PluginToolWindowManager().init();
	}

	private static void runAllPlugins() {
		ApplicationManager.getApplication().invokeLater(new Runnable() {
			@Override public void run() {
				AnActionEvent event = new AnActionEvent(
						null,
						Util.DUMMY_DATA_CONTEXT,
						Evaluator.RUN_ALL_PLUGINS_ON_IDE_START,
						new Presentation(),
						ActionManager.getInstance(),
						0
				);
				EvaluatePluginAction.evaluatePlugins(pluginIdToPathMap().keySet(), event);
			}
		});
	}

	private static void installHelloWorldPlugin() {
		ExamplePluginInstaller pluginInstaller = new ExamplePluginInstaller(PLUGIN_EXAMPLES_PATH + "/helloWorld", asList("plugin.groovy"));
		pluginInstaller.installPlugin(new ExamplePluginInstaller.Listener() {
			@Override public void onException(Exception e, String pluginPath) {
				LOG.warn("Failed to install plugin: " + pluginPath, e);
			}
		});
	}

	@Override public void disposeComponent() {
	}

	@Override
	@NotNull
	public String getComponentName() {
		return "EvalComponent";
	}
}
