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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import liveplugin.IDEUtil;
import liveplugin.LivePluginAppComponent;
import liveplugin.Settings;
import liveplugin.toolwindow.PluginToolWindowManager;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

import static com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT;
import static com.intellij.util.containers.ContainerUtil.find;
import static com.intellij.util.containers.ContainerUtil.map;
import static java.util.Collections.emptyList;
import static liveplugin.IDEUtil.SingleThreadBackgroundRunner;
import static liveplugin.LivePluginAppComponent.*;
import static liveplugin.pluginrunner.GroovyPluginRunner.MAIN_SCRIPT;
import static liveplugin.pluginrunner.PluginRunner.IDE_STARTUP;

public class RunPluginAction extends AnAction implements DumbAware {
	private static final SingleThreadBackgroundRunner backgroundRunner = new SingleThreadBackgroundRunner("LivePlugin thread");
	private static final Function<Runnable,Void> RUN_ON_EDT = new Function<Runnable, Void>() {
		@Override public Void fun(Runnable runnable) {
			UIUtil.invokeAndWaitIfNeeded(runnable);
			return null;
		}
	};
    private static final String DISPOSABLE_KEY = "pluginDisposable";
    private static final WeakHashMap<String, Map<String, Object>> bindingByPluginId = new WeakHashMap<String, Map<String, Object>>();


    public RunPluginAction() {
		super("Run Plugin", "Run selected plugins", IDEUtil.RUN_PLUGIN_ICON);
	}

    @Override public void actionPerformed(@NotNull AnActionEvent event) {
        runCurrentPlugin(event);
    }

    @Override public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabled(!findCurrentPluginIds(event).isEmpty());
    }

    public static void runPlugins(final Collection<String> pluginIds, AnActionEvent event,
                                  final ErrorReporter errorReporter, final List<PluginRunner> pluginRunners) {
        checkThatGroovyIsOnClasspath();

        final Project project = event.getProject();
        final boolean isIdeStartup = event.getPlace().equals(IDE_STARTUP);

        if (!isIdeStartup) {
            Settings.countPluginsUsage(pluginIds);
        }

        Runnable runPlugins = new Runnable() {
            @Override public void run() {
                for (final String pluginId : pluginIds) {
                    try {
                        final String pathToPluginFolder = LivePluginAppComponent.pluginIdToPathMap().get(pluginId); // TODO not thread-safe
                        PluginRunner pluginRunner = find(pluginRunners, new Condition<PluginRunner>() {
                            @Override public boolean value(PluginRunner it) {
                                return pathToPluginFolder != null && it.canRunPlugin(pathToPluginFolder);
                            }
                        });
                        if (pluginRunner == null) {
                            List<String> scriptNames = map(pluginRunners, new Function<PluginRunner, String>() {
                                @Override public String fun(PluginRunner it) {
                                    return it.scriptName();
                                }
                            });
                            errorReporter.addNoScriptError(pluginId, scriptNames);
                        } else {
                            final Map<String, Object> oldBinding = bindingByPluginId.get(pluginId);
                            if (oldBinding != null) {
	                            ApplicationManager.getApplication().invokeLater(new Runnable() {
		                            @Override public void run() {
			                            try {
				                            Disposer.dispose((Disposable) oldBinding.get(DISPOSABLE_KEY));
			                            } catch (Exception e) {
				                            errorReporter.addRunningError(pluginId, e);
			                            }
		                            }
	                            });
                            }
                            Map<String, Object> binding = createBinding(pathToPluginFolder, project, isIdeStartup);
                            bindingByPluginId.put(pluginId, binding);

                            pluginRunner.runPlugin(pathToPluginFolder, pluginId, binding, RUN_ON_EDT);
                        }
                    } catch (Exception e) {
                        errorReporter.addLoadingError(pluginId, e);
                    } finally {
                        errorReporter.reportAllErrors(new ErrorReporter.Callback() {
                            @Override public void display(String title, String message) {
                                IDEUtil.displayError(title, message, ERROR_OUTPUT, project);
                            }
                        });
                    }
                }
            }
        };

        backgroundRunner.run(project, "Loading plugin", runPlugins);
    }

	public static List<PluginRunner> createPluginRunners(ErrorReporter errorReporter) {
		List<PluginRunner> result = new ArrayList<PluginRunner>();
		result.add(new GroovyPluginRunner(MAIN_SCRIPT, errorReporter, environment()));
		if (scalaIsOnClassPath()) result.add(new ScalaPluginRunner(errorReporter, environment()));
		if (clojureIsOnClassPath()) result.add(new ClojurePluginRunner(errorReporter, environment()));
		return result;
	}

	private static Map<String, Object> createBinding(final String pathToPluginFolder, Project project, boolean isIdeStartup) {
		Disposable disposable = new Disposable() {
			@Override public void dispose() {}
			@Override public String toString() {
				return "LivePlugin: " + pathToPluginFolder;
			}
		};
		Disposer.register(ApplicationManager.getApplication(), disposable);

		Map<String, Object> binding = new HashMap<String, Object>();
		binding.put("project", project);
		binding.put("isIdeStartup", isIdeStartup);
		binding.put("pluginPath", pathToPluginFolder);
		binding.put(DISPOSABLE_KEY, disposable);
		return binding;
	}

	static Map<String, String> environment() {
		return new HashMap<String, String>(System.getenv());
	}

	static List<String> findCurrentPluginIds(AnActionEvent event) {
		List<String> pluginIds = pluginsSelectedInToolWindow(event);
		if (!pluginIds.isEmpty() && pluginToolWindowHasFocus(event)) {
			return pluginIds;
		} else {
			return pluginForCurrentlyOpenFile(event);
		}
	}

	private static boolean pluginToolWindowHasFocus(AnActionEvent event) {
		PluginToolWindowManager.PluginToolWindow pluginToolWindow = PluginToolWindowManager.getToolWindowFor(event.getProject());
		return pluginToolWindow != null && pluginToolWindow.isActive();
	}

	private static List<String> pluginsSelectedInToolWindow(AnActionEvent event) { // TODO get selected plugins through DataContext
		PluginToolWindowManager.PluginToolWindow pluginToolWindow = PluginToolWindowManager.getToolWindowFor(event.getProject());
		if (pluginToolWindow == null) return emptyList();
		return pluginToolWindow.selectedPluginIds();
	}

	private static List<String> pluginForCurrentlyOpenFile(AnActionEvent event) {
		Project project = event.getProject();
		if (project == null) return emptyList();
		Editor selectedTextEditor = FileEditorManager.getInstance(project).getSelectedTextEditor();
		if (selectedTextEditor == null) return emptyList();

		VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(selectedTextEditor.getDocument());
		if (virtualFile == null) return emptyList();

		final File file = new File(virtualFile.getPath());
		Map.Entry<String, String> entry = find(LivePluginAppComponent.pluginIdToPathMap().entrySet(), new Condition<Map.Entry<String, String>>() {
			@Override
			public boolean value(Map.Entry<String, String> entry) {
				String pluginPath = entry.getValue();
				return FileUtil.isAncestor(new File(pluginPath), file, false);
			}
		});
		if (entry == null) return emptyList();
		return Collections.singletonList(entry.getKey());
	}

	private void runCurrentPlugin(AnActionEvent event) {
		IDEUtil.saveAllFiles();
		List<String> pluginIds = findCurrentPluginIds(event);
		ErrorReporter errorReporter = new ErrorReporter();
		runPlugins(pluginIds, event, errorReporter, createPluginRunners(errorReporter));
	}
}
