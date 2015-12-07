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
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import liveplugin.pluginrunner.ErrorReporter;
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

import static com.intellij.openapi.project.Project.DIRECTORY_STORE_FOLDER;
import static java.util.Arrays.asList;
import static liveplugin.IDEUtil.askIfUserWantsToRestartIde;
import static liveplugin.IDEUtil.downloadFile;

public class LivePluginAppComponent implements ApplicationComponent, DumbAware {
	public static final String PLUGIN_EXAMPLES_PATH = "/liveplugin/pluginexamples";
	public static final String LIVEPLUGIN_LIBS_PATH = PathManager.getPluginsPath() + "/LivePlugin/lib/";
	public static final NotificationGroup livePluginNotificationGroup = NotificationGroup.balloonGroup("Live Plugin");

	private static final Logger LOG = Logger.getInstance(LivePluginAppComponent.class);
	private static final String DEFAULT_PLUGIN_PATH = PLUGIN_EXAMPLES_PATH;
	private static final String DEFAULT_PLUGIN_SCRIPT = "default-plugin.groovy";
	private static final String DEFAULT_PLUGIN_TEST_SCRIPT = "default-plugin-test.groovy";

	private static final String DEFAULT_IDEA_OUTPUT_FOLDER = "out";
	private static final String COMPONENT_NAME = "LivePluginComponent";

	public static String pluginsRootPath() {
		return FileUtilRt.toSystemIndependentName(PathManager.getPluginsPath() + "/live-plugins");
	}

	public static Map<String, String> pluginIdToPathMap() {
		final boolean containsIdeaProjectFolder = new File(pluginsRootPath() + "/" + DIRECTORY_STORE_FOLDER).exists();

		File[] files = new File(pluginsRootPath()).listFiles(new FileFilter() {
			@SuppressWarnings("SimplifiableIfStatement")
			@Override public boolean accept(@NotNull File file) {
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
			@Override public boolean accept(@NotNull File dir, @NotNull String name) {
				return name.equals(GroovyPluginRunner.MAIN_SCRIPT);
			}
		});
		return files.length < 1;
	}

	public static String defaultPluginScript() {
		return readSampleScriptFile(DEFAULT_PLUGIN_PATH, DEFAULT_PLUGIN_SCRIPT);
	}

	public static String defaultPluginTestScript() {
		return readSampleScriptFile(DEFAULT_PLUGIN_PATH, DEFAULT_PLUGIN_TEST_SCRIPT);
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
		return IDEUtil.isOnClasspath("org.codehaus.groovy.runtime.DefaultGroovyMethods");
	}

	public static boolean scalaIsOnClassPath() {
		return IDEUtil.isOnClasspath("scala.Some");
	}

	public static boolean clojureIsOnClassPath() {
		return IDEUtil.isOnClasspath("clojure.core.Vec");
	}

	private static void runAllPlugins() {
		ApplicationManager.getApplication().invokeLater(new Runnable() {
			@Override public void run() {
				AnActionEvent event = new AnActionEvent(
						null,
						IDEUtil.DUMMY_DATA_CONTEXT,
						PluginRunner.IDE_STARTUP,
						new Presentation(),
						ActionManager.getInstance(),
						0
				);
				ErrorReporter errorReporter = new ErrorReporter();
				RunPluginAction.runPlugins(pluginIdToPathMap().keySet(), event, errorReporter, RunPluginAction.createPluginRunners(errorReporter));
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

	public static void checkThatGroovyIsOnClasspath() {
		final File oldGroovyLibrary = new File(LIVEPLUGIN_LIBS_PATH + File.separator + "groovy-all-2.0.6.jar"); // TODO update groovy version

		NotificationListener listener = new NotificationListener() {
			@Override public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
				if (oldGroovyLibrary.exists()) {
					FileUtil.delete(oldGroovyLibrary);
				}

				boolean downloaded = downloadFile("http://repo1.maven.org/maven2/org/codehaus/groovy/groovy-all/2.2.1/", "groovy-all-2.2.1.jar", LIVEPLUGIN_LIBS_PATH);
				if (downloaded) {
					notification.expire();
					askIfUserWantsToRestartIde("For Groovy libraries to be loaded IDE restart is required. Restart now?");
				} else {
					livePluginNotificationGroup
							.createNotification("Failed to download Groovy libraries", NotificationType.WARNING);
				}
			}
		};

		if (oldGroovyLibrary.exists()) {
			livePluginNotificationGroup.createNotification(
					"There is old version of groovy library on LivePlugin classpath",
					"It might work incorrectly. <a href=\"\">Click here to update groovy to 2.2.1</a> (~6Mb)",
					NotificationType.ERROR,
					listener
			).notify(null);
		}

		if (isGroovyOnClasspath()) return;

		livePluginNotificationGroup.createNotification(
				"LivePlugin didn't find Groovy libraries on classpath",
				"Without it plugins won't work. <a href=\"\">Download Groovy libraries</a> (~6Mb)",
				NotificationType.ERROR,
				listener
		).notify(null);
	}

	@Override public void initComponent() {
		checkThatGroovyIsOnClasspath();

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

	@Override public void disposeComponent() {
	}

	@Override @NotNull public String getComponentName() {
		return COMPONENT_NAME;
	}
}
