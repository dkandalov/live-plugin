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

import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.unscramble.UnscrambleDialog;
import com.intellij.util.Function;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;

import static com.intellij.openapi.ui.Messages.showOkCancelDialog;
import static com.intellij.util.containers.ContainerUtil.map;
import static java.util.Arrays.asList;
import static java.util.concurrent.Executors.newSingleThreadExecutor;

public class IDEUtil {
	public static final Icon ADD_PLUGIN_ICON = AllIcons.General.Add;
	public static final Icon DELETE_PLUGIN_ICON = AllIcons.General.Remove;
	public static final Icon REFRESH_PLUGIN_LIST_ICON = AllIcons.Actions.Refresh;
	public static final Icon PLUGIN_ICON = AllIcons.Nodes.Plugin;
	// this is resized icon because IntelliJ requires toolwindow icons to be 13x13
	public static final Icon PLUGIN_TOOLWINDOW_ICON = (
			UIUtil.isUnderDarcula() ?
				IconLoader.getIcon("/liveplugin/plugin-toolwindow-icon_dark.png") :
				IconLoader.getIcon("/liveplugin/plugin-toolwindow-icon.png")
	);

	public static final Icon RUN_PLUGIN_ICON = AllIcons.Actions.Execute;
	public static final Icon TEST_PLUGIN_ICON = AllIcons.RunConfigurations.Junit;
	public static final Icon EXPAND_ALL_ICON = AllIcons.Actions.Expandall;
	public static final Icon COLLAPSE_ALL_ICON = AllIcons.Actions.Collapseall;
	public static final Icon SETTINGS_ICON = AllIcons.General.ProjectSettings;
	public static final Icon HELP_ICON = AllIcons.Actions.Help;

    public static final Icon INTEGRATION_TAB_ICON = AllIcons.Nodes.TestSourceFolder;
    public static final Icon RERUN_PLUGIN_TEST_ICON = RUN_PLUGIN_ICON;

    public static final FileType GROOVY_FILE_TYPE = FileTypeManager.getInstance().getFileTypeByExtension(".groovy");
	public static final FileType SCALA_FILE_TYPE = FileTypeManager.getInstance().getFileTypeByExtension(".scala");
	public static final FileType CLOJURE_FILE_TYPE = FileTypeManager.getInstance().getFileTypeByExtension(".clj");
	public static final DataContext DUMMY_DATA_CONTEXT = new DataContext() {
		@Nullable @Override public Object getData(@NonNls String dataId) {
			return null;
		}
	};
	private static final Logger LOG = Logger.getInstance(IDEUtil.class);

    public static ConsoleView displayInConsole(String consoleTitle, String text, ConsoleViewContentType contentType, Project project) {
		if (project == null) {
			LOG.warn("Failed to display console because project was 'null'. Text not shown in console: " + text);
			return null;
		}
		return PluginUtil.showInConsole(text, consoleTitle, project, contentType);
	}

	public static void showErrorDialog(Project project, String message, String title) {
		Messages.showMessageDialog(project, message, title, Messages.getErrorIcon());
	}

	public static void saveAllFiles() {
		ApplicationManager.getApplication().runWriteAction(new Runnable() {
			public void run() {
				FileDocumentManager.getInstance().saveAllDocuments();
			}
		});
	}

	public static void runAction(final AnAction action, String place) {
		final AnActionEvent event = new AnActionEvent(
				null,
				DUMMY_DATA_CONTEXT,
				place,
				action.getTemplatePresentation(),
				ActionManager.getInstance(),
				0
		);
		ApplicationManager.getApplication().invokeLater(new Runnable() {
			@Override public void run() {
				action.actionPerformed(event);
			}
		});
	}

	public static boolean isOnClasspath(String className) {
		URL resource = IDEUtil.class.getClassLoader().getResource(className.replace(".", "/") + ".class");
		return resource != null;
	}

	public static void askIfUserWantsToRestartIde(String message) {
		int answer = showOkCancelDialog(message, "Restart Is Required", "Restart", "Postpone", Messages.getQuestionIcon());
		if (answer == Messages.OK) {
			ApplicationManagerEx.getApplicationEx().restart(true);
		}
	}

	public static boolean downloadFile(String downloadUrl, String fileName, String targetPath) {
		//noinspection unchecked
		return downloadFiles(asList(Pair.create(downloadUrl, fileName)), targetPath);
	}

	public static boolean downloadFiles(List<Pair<String, String>> urlAndFileNames, String targetPath) {
		final DownloadableFileService service = DownloadableFileService.getInstance();
		List<DownloadableFileDescription> descriptions = map(urlAndFileNames, new Function<Pair<String, String>, DownloadableFileDescription>() {
			@Override public DownloadableFileDescription fun(Pair<String, String> it) {
				return service.createFileDescription(it.first + it.second, it.second);
			}
		});
		List<VirtualFile> files = service.createDownloader(descriptions, "").downloadFilesWithProgress(targetPath, null, null);
		return files != null && files.size() == urlAndFileNames.size();
	}

	public static String unscrambleThrowable(Throwable throwable) {
		StringWriter writer = new StringWriter();
		//noinspection ThrowableResultOfMethodCallIgnored
		throwable.printStackTrace(new PrintWriter(writer));
		return Unscramble.normalizeText(writer.getBuffer().toString());
	}

	public static class SingleThreadBackgroundRunner {
		private final ExecutorService singleThreadExecutor;

		public SingleThreadBackgroundRunner(final String threadName) {
			singleThreadExecutor = newSingleThreadExecutor(new ThreadFactory() {
				@NotNull @Override public Thread newThread(@NotNull Runnable runnable) {
					return new Thread(runnable, threadName);
				}
			});
		}

		public void run(Project project, String taskDescription, final Runnable runnable) {
			new Task.Backgroundable(project, taskDescription, false, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
				@Override public void run(@NotNull ProgressIndicator indicator) {
					try {
						singleThreadExecutor.submit(runnable).get();
					} catch (InterruptedException ignored) {
					} catch (ExecutionException ignored) {
					}
				}
			}.queue();
		}
	}

	/**
	 * Copy-pasted from {@link UnscrambleDialog#normalizeText(String)}
	 * because PhpStorm doesn't have this class.
	 */
	private static class Unscramble {
		public static String normalizeText(@NonNls String text) {
			StringBuilder builder = new StringBuilder(text.length());

			text = text.replaceAll("(\\S[ \\t\\x0B\\f\\r]+)(at\\s+)", "$1\n$2");
			String[] lines = text.split("\n");

			boolean first = true;
			boolean inAuxInfo = false;
			for (String line : lines) {
				//noinspection HardCodedStringLiteral
				if (!inAuxInfo && (line.startsWith("JNI global references") || line.trim().equals("Heap"))) {
					builder.append("\n");
					inAuxInfo = true;
				}
				if (inAuxInfo) {
					builder.append(trimSuffix(line)).append("\n");
					continue;
				}
				if (!first && mustHaveNewLineBefore(line)) {
					builder.append("\n");
					if (line.startsWith("\"")) builder.append("\n"); // Additional line break for thread names
				}
				first = false;
				int i = builder.lastIndexOf("\n");
				CharSequence lastLine = i == -1 ? builder : builder.subSequence(i + 1, builder.length());
				if (lastLine.toString().matches("\\s*at") && !line.matches("\\s+.*")) builder.append(" "); // separate 'at' from file name
				builder.append(trimSuffix(line));
			}
			return builder.toString();
		}

		@SuppressWarnings("RedundantIfStatement")
		private static boolean mustHaveNewLineBefore(String line) {
			final int nonWs = CharArrayUtil.shiftForward(line, 0, " \t");
			if (nonWs < line.length()) {
				line = line.substring(nonWs);
			}

			if (line.startsWith("at")) return true;        // Start of the new stack frame entry
			if (line.startsWith("Caused")) return true;    // Caused by message
			if (line.startsWith("- locked")) return true;  // "Locked a monitor" logging
			if (line.startsWith("- waiting")) return true; // "Waiting for monitor" logging
			if (line.startsWith("- parking to wait")) return true;
			if (line.startsWith("java.lang.Thread.State")) return true;
			if (line.startsWith("\"")) return true;        // Start of the new thread (thread name)

			return false;
		}

		private static String trimSuffix(final String line) {
			int len = line.length();

			while ((0 < len) && (line.charAt(len-1) <= ' ')) {
				len--;
			}
			return (len < line.length()) ? line.substring(0, len) : line;
		}
	}
}
