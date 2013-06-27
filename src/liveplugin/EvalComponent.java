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

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import liveplugin.toolwindow.PluginToolWindowManager;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.vfs.VfsUtilCore.pathToUrl;
import static java.util.Arrays.asList;
import static liveplugin.toolwindow.PluginToolWindowManager.ExamplePluginInstaller;

public class EvalComponent implements ApplicationComponent { // TODO implement DumbAware?
	private static final Logger LOG = Logger.getInstance(EvalComponent.class);

	public static final String MAIN_SCRIPT = "plugin.groovy";
	public static final String PLUGIN_EXAMPLES_PATH = "/liveplugin/pluginexamples";

	private static final String DEFAULT_PLUGIN_PATH = PLUGIN_EXAMPLES_PATH;
	private static final String DEFAULT_PLUGIN_SCRIPT = "default-plugin.groovy";

	private static final String DEFAULT_IDEA_OUTPUT_FOLDER = "out";
	private static final String COMPONENT_NAME = "LivePluginComponent";

	public static String pluginsRootPath() {
		return FileUtilRt.toSystemIndependentName(PathManager.getPluginsPath() + "/live-plugins");
	}
	private static String oldPluginsRootPath() {
		return FileUtilRt.toSystemIndependentName(PathManager.getPluginsPath() + "/intellij-eval-plugins");
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
			result.put(file.getName(), FileUtilRt.toSystemIndependentName(file.getAbsolutePath()));
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
		checkGroovyLibsAreOnClasspath();

		Settings settings = Settings.getInstance();
		if (settings.justInstalled) {
			installHelloWorldPlugin();
			settings.justInstalled = false;
		}
		if (new File(oldPluginsRootPath()).exists()) {
			Migration.askIfUserWantsToMigrate(new Runnable() {
				@Override public void run() {
					Migration.migrateOldPlugins();
				}
			});
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

	@Override @NotNull public String getComponentName() {
		return COMPONENT_NAME;
	}

	private static void checkGroovyLibsAreOnClasspath() {
		boolean found;
		try {
			Class.forName("org.codehaus.groovy.runtime.DefaultGroovyMethods");
			found = true;
		} catch (ClassNotFoundException ignored) {
			found = false;
		}

		if (!found) {
			NotificationGroup.balloonGroup("Live Plugin").createNotification(
					"LivePlugin didn't find Groovy on classpath.",
					"Without it plugins won't work. It should be possible to fix this problem<br/> " +
							"by copying groovy-all.jar to '" + PathManager.getLibPath() + "'",
					NotificationType.WARNING,
					null
			).notify(null);
		}
	}

	private static class Migration {
		private static final String OLD_STATIC_IMPORT = "import static intellijeval";
		private static final String OLD_IMPORT = "import intellijeval";
		private static final String NEW_STATIC_IMPORT = "import static liveplugin";
		private static final String NEW_IMPORT = "import liveplugin";

		public static void askIfUserWantsToMigrate(final Runnable migrationCallback) {
			NotificationListener listener = new NotificationListener() {
				@Override public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
					migrationCallback.run();
					notification.expire();
				}
			};

			NotificationGroup.balloonGroup("Live Plugin Migration").createNotification(
					"Detected plugins for IntelliJEval.",
					"<a href=\"\">Migrate plugins.</a> This includes replacing 'intellijeval' with 'liveplugin' package name in imports.",
					NotificationType.INFORMATION,
					listener
			).notify(null);
		}

		public static void migrateOldPlugins() {
			try {
				FileUtil.rename(new File(oldPluginsRootPath()), new File(pluginsRootPath()));
				for (File file : allFilesInDirectory(new File(pluginsRootPath()))) {
					migrate(file);
				}
			} catch (IOException e) {
				LOG.error("Error while migrating old plugins", e);
			}
		}

		private static void migrate(File file) throws IOException {
			String text = FileUtil.loadFile(file);
			if (text.contains(OLD_STATIC_IMPORT) || text.contains(OLD_IMPORT)) {
				String[] lines = text.split("\n");
				for (int i = 0; i < lines.length; i++) {
					lines[i] = lines[i].replace(OLD_STATIC_IMPORT, NEW_STATIC_IMPORT).replace(OLD_IMPORT, NEW_IMPORT);
				}
				FileUtil.writeToFile(file, StringUtil.join(lines, "\n"));

				VirtualFileManager.getInstance().refreshAndFindFileByUrl(pathToUrl(toSystemIndependentName(pluginsRootPath())));
			}
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
}
