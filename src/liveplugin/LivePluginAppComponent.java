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

import com.intellij.ide.plugins.PluginManager;
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
import liveplugin.pluginrunner.GroovyPluginRunner;
import liveplugin.pluginrunner.PluginRunner;
import liveplugin.pluginrunner.RunPluginAction;
import liveplugin.toolwindow.PluginToolWindowManager;
import liveplugin.toolwindow.util.ExamplePluginInstaller;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;
import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static com.intellij.openapi.extensions.PluginId.getId;
import static com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;
import static com.intellij.openapi.vfs.VfsUtilCore.pathToUrl;
import static java.util.Arrays.asList;
import static liveplugin.IdeUtil.askIsUserWantsToRestartIde;
import static liveplugin.IdeUtil.downloadFile;
import static liveplugin.MyFileUtil.allFilesInDirectory;

public class LivePluginAppComponent implements ApplicationComponent { // TODO implement DumbAware?
	private static final Logger LOG = Logger.getInstance(LivePluginAppComponent.class);

	public static final String PLUGIN_EXAMPLES_PATH = "/liveplugin/pluginexamples";
	public static final String LIVEPLUGIN_LIBS_PATH = PathManager.getPluginsPath() + "/LivePlugin/lib/";

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
				return name.equals(GroovyPluginRunner.MAIN_SCRIPT);
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
			return FileUtil.loadTextAndClose(LivePluginAppComponent.class.getClassLoader().getResourceAsStream(path));
		} catch (IOException e) {
			LOG.error(e);
			return "";
		}
	}

	public static boolean pluginExists(String pluginId) {
		return pluginIdToPathMap().keySet().contains(pluginId);
	}

	private static boolean isGroovyOnClasspath() {
		return IdeUtil.isOnClasspath("org.codehaus.groovy.runtime.DefaultGroovyMethods");
	}

	public static boolean scalaIsOnClassPath() {
		return IdeUtil.isOnClasspath("scala.Some");
	}

	public static boolean clojureIsOnClassPath() {
		return IdeUtil.isOnClasspath("clojure.core.Vec");
	}

	@Override public void initComponent() {
		if (PluginManager.isPluginInstalled(getId("IntelliJEval"))) {
			NotificationGroup.balloonGroup("Live Plugin").createNotification(
					"It seems that you IntelliJEval plugin enabled.<br/>Please disable it to use LivePlugin.",
					NotificationType.ERROR
			).notify(null);
			return;
		}
		checkThatGroovyIsOnClasspath();

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
						IdeUtil.DUMMY_DATA_CONTEXT,
						PluginRunner.IDE_STARTUP,
						new Presentation(),
						ActionManager.getInstance(),
						0
				);
				RunPluginAction.runPlugins(pluginIdToPathMap().keySet(), event);
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

	public static void checkThatGroovyIsOnClasspath() {
		if (isGroovyOnClasspath()) return;

		NotificationListener listener = new NotificationListener() {
			@Override public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
				boolean downloaded = downloadFile("http://repo1.maven.org/maven2/org/codehaus/groovy/groovy-all/2.0.6/", "groovy-all-2.0.6.jar", LIVEPLUGIN_LIBS_PATH);
				if (downloaded) {
					notification.expire();
					askIsUserWantsToRestartIde("For Groovy libraries to be loaded IDE restart is required. Restart now?");
				} else {
					NotificationGroup.balloonGroup("Live Plugin")
							.createNotification("Failed to download Groovy libraries", NotificationType.WARNING);
				}
			}
		};
		NotificationGroup.balloonGroup("Live Plugin").createNotification(
				"LivePlugin didn't find Groovy libraries on classpath",
				"Without it plugins won't work. <a href=\"\">Download Groovy libraries</a> (~6Mb)",
				NotificationType.ERROR,
				listener
		).notify(null);
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
					"Detected plugins for IntelliJEval",
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
	}
}
