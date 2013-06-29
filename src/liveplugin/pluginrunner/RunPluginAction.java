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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import liveplugin.IdeUtil;
import liveplugin.LivePluginAppComponent;
import liveplugin.toolwindow.PluginToolWindowManager;

import java.io.File;
import java.util.*;

public class RunPluginAction extends AnAction {
	public RunPluginAction() {
		super("Run Plugin", "Run selected plugins", IdeUtil.RUN_PLUGIN_ICON);
	}

	@Override public void actionPerformed(AnActionEvent event) {
		runCurrentPlugin(event);
	}

	@Override public void update(AnActionEvent event) {
		event.getPresentation().setEnabled(!findCurrentPluginIds(event).isEmpty());
	}

	private void runCurrentPlugin(AnActionEvent event) {
		IdeUtil.saveAllFiles();
		List<String> pluginIds = findCurrentPluginIds(event);
		runPlugins(pluginIds, event);
	}

	public static void runPlugins(Collection<String> pluginIds, AnActionEvent event) {
		ErrorReporter errorReporter = new ErrorReporter();
		List<PluginRunner> pluginRunners = createPluginRunners(errorReporter);

		for (String pluginId : pluginIds) {
			final String pathToPluginFolder = LivePluginAppComponent.pluginIdToPathMap().get(pluginId);
			PluginRunner pluginRunner = ContainerUtil.find(pluginRunners, new Condition<PluginRunner>() {
				@Override public boolean value(PluginRunner it) {
					return it.canRunPlugin(pathToPluginFolder);
				}
			});
			if (pluginRunner == null) {
				errorReporter.addLoadingError(pluginId, "Couldn't find plugin startup script");
				continue;
			}

			pluginRunner.runPlugin(pathToPluginFolder, pluginId, createBinding(event, pathToPluginFolder));
		}

		errorReporter.reportLoadingErrors(event);
		errorReporter.reportRunningPluginExceptions(event);
	}

	private static List<PluginRunner> createPluginRunners(ErrorReporter errorReporter) {
		return Arrays.asList(
				new GroovyPluginRunner(errorReporter, environment()),
				new ScalaPluginRunner(errorReporter, environment())
		);
	}

	private static Map<String, Object> createBinding(AnActionEvent event, String pathToPluginFolder) {
		Map<String, Object> binding = new HashMap<String, Object>();
		binding.put("event", event);
		binding.put("project", event.getProject());
		binding.put("isIdeStartup", event.getPlace().equals(PluginRunner.IDE_STARTUP));
		binding.put("pluginPath", pathToPluginFolder);
		return binding;
	}

	private static Map<String, String> environment() {
		Map<String, String> result = new HashMap<String, String>(System.getenv());
		result.put("INTELLIJ_PLUGINS_PATH", PathManager.getPluginsPath());
		result.put("INTELLIJ_LIBS", PathManager.getLibPath());
		return result;
	}

	private static List<String> findCurrentPluginIds(AnActionEvent event) {
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
		if (pluginToolWindow == null) return Collections.emptyList();
		return pluginToolWindow.selectedPluginIds();
	}

	private static List<String> pluginForCurrentlyOpenFile(AnActionEvent event) {
		Project project = event.getProject();
		if (project == null) return Collections.emptyList();
		Editor selectedTextEditor = FileEditorManager.getInstance(project).getSelectedTextEditor();
		if (selectedTextEditor == null) return Collections.emptyList();

		VirtualFile virtualFile = FileDocumentManager.getInstance().getFile(selectedTextEditor.getDocument());
		if (virtualFile == null) return Collections.emptyList();

		final File file = new File(virtualFile.getPath());
		Map.Entry<String, String> entry = ContainerUtil.find(LivePluginAppComponent.pluginIdToPathMap().entrySet(), new Condition<Map.Entry<String, String>>() {
			@Override
			public boolean value(Map.Entry<String, String> entry) {
				String pluginPath = entry.getValue();
				return FileUtil.isAncestor(new File(pluginPath), file, false);
			}
		});
		if (entry == null) return Collections.emptyList();
		return Collections.singletonList(entry.getKey());
	}
}
