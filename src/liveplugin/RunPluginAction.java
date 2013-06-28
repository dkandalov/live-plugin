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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import liveplugin.toolwindow.PluginToolWindowManager;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

	static void runPlugins(Collection<String> pluginIds, AnActionEvent event) {
		ErrorReporter errorReporter = new ErrorReporter();
		PluginRunner pluginRunner = new GroovyPluginRunner(errorReporter);

		for (String pluginId : pluginIds) {
			String path = LivePluginComponent.pluginIdToPathMap().get(pluginId);
			pluginRunner.runPlugin(pluginId, path, event);
		}

		errorReporter.reportLoadingErrors(event);
		errorReporter.reportRunningPluginExceptions(event);
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
		Map.Entry<String, String> entry = ContainerUtil.find(LivePluginComponent.pluginIdToPathMap().entrySet(), new Condition<Map.Entry<String, String>>() {
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
