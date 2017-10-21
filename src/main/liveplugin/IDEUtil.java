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

import com.intellij.diagnostic.PluginException;
import com.intellij.execution.ExecutionManager;
import com.intellij.execution.Executor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.TextConsoleBuilderFactory;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.actions.CloseAction;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.download.DownloadableFileDescription;
import com.intellij.util.download.DownloadableFileService;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

import static com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT;
import static com.intellij.openapi.ui.Messages.showOkCancelDialog;
import static com.intellij.util.containers.ContainerUtil.map;
import static java.util.Arrays.asList;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static liveplugin.LivePluginAppComponent.livePluginId;

public class IDEUtil {
	public static final FileType groovyFileType = FileTypeManager.getInstance().getFileTypeByExtension(".groovy");
	public static final FileType kotlinFileType = KotlinScriptFileType.INSTANCE;
	public static final FileType scalaFileType = FileTypeManager.getInstance().getFileTypeByExtension(".scala");
	public static final FileType clojureFileType = FileTypeManager.getInstance().getFileTypeByExtension(".clj");
	public static final DataContext dummyDataContext = dataId -> null;
	private static final Logger logger = Logger.getInstance(IDEUtil.class);

	public static void displayError(String consoleTitle, String text, Project project) {
		if (project == null) {
			// "project" can be null when there are no open projects or while IDE is loading.
			// It is important to log error specifying plugin id, otherwise IDE will try to guess
			// plugin id based on classes in stacktrace and might get it wrong,
			// e.g. if activity tracker plugin is installed, it will include LivePlugin classes as library
			// (see com.intellij.diagnostic.IdeErrorsDialog.findPluginId)
			logger.error(consoleTitle, new PluginException(text, PluginId.getId(livePluginId)));
		} else {
			showInConsole(text, consoleTitle, project, ERROR_OUTPUT);
		}
	}

	public static void showErrorDialog(Project project, String message, String title) {
		Messages.showMessageDialog(project, message, title, Messages.getErrorIcon());
	}

	public static void saveAllFiles() {
		ApplicationManager.getApplication().runWriteAction(() -> FileDocumentManager.getInstance().saveAllDocuments());
	}

	public static void performAction(final AnAction action, String place) {
		final AnActionEvent event = new AnActionEvent(
			null,
			dummyDataContext,
			place,
			action.getTemplatePresentation(),
			ActionManager.getInstance(),
			0
		);
		ApplicationManager.getApplication().invokeLater(() -> action.actionPerformed(event));
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

	// TODO make download non-modal
	public static boolean downloadFiles(List<Pair<String, String>> urlAndFileNames, String targetPath) {
		final DownloadableFileService service = DownloadableFileService.getInstance();
		List<DownloadableFileDescription> descriptions = map(urlAndFileNames, it -> service.createFileDescription(it.first + it.second, it.second));
		List<VirtualFile> files = service.createDownloader(descriptions, "").downloadFilesWithProgress(targetPath, null, null);
		return files != null && files.size() == urlAndFileNames.size();
	}

	public static String unscrambleThrowable(Throwable throwable) {
		StringWriter writer = new StringWriter();
		//noinspection ThrowableResultOfMethodCallIgnored
		throwable.printStackTrace(new PrintWriter(writer));
		return Unscramble.normalizeText(writer.getBuffer().toString());
	}

	private static void showInConsole(final String message, final String consoleTitle, @NotNull final Project project,
	                                  final ConsoleViewContentType contentType) {
		Runnable runnable = () -> {
			ConsoleView console = TextConsoleBuilderFactory.getInstance().createBuilder(project).getConsole();
			console.print(message, contentType);

			DefaultActionGroup toolbarActions = new DefaultActionGroup();
			JPanel consoleComponent = new MyConsolePanel(console, toolbarActions);
			RunContentDescriptor descriptor = new RunContentDescriptor(console, null, consoleComponent, consoleTitle) {
				@Override public boolean isContentReuseProhibited() {
					return true;
				}

				@Override public Icon getIcon() {
					return AllIcons.Nodes.Plugin;
				}
			};
			Executor executor = DefaultRunExecutor.getRunExecutorInstance();

			toolbarActions.add(new CloseAction(executor, descriptor, project));
			for (AnAction anAction : console.createConsoleActions()) {
				toolbarActions.add(anAction);
			}

			ExecutionManager.getInstance(project).getContentManager().showRunContent(executor, descriptor);
		};
		ApplicationManager.getApplication().invokeAndWait(runnable, ModalityState.NON_MODAL);
	}

	private static class MyConsolePanel extends JPanel {
		MyConsolePanel(ExecutionConsole consoleView, ActionGroup toolbarActions) {
			super(new BorderLayout());
			JPanel toolbarPanel = new JPanel(new BorderLayout());
			toolbarPanel.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions, false).getComponent());
			add(toolbarPanel, BorderLayout.WEST);
			add(consoleView.getComponent(), BorderLayout.CENTER);
		}
	}


	public static class SingleThreadBackgroundRunner {
		private final ExecutorService singleThreadExecutor;

		public SingleThreadBackgroundRunner(final String threadName) {
			singleThreadExecutor = newSingleThreadExecutor(runnable -> new Thread(runnable, threadName));
		}

		public void run(Project project, String taskDescription, final Runnable runnable) {
			new Task.Backgroundable(project, taskDescription, false, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
				@Override public void run(@NotNull ProgressIndicator indicator) {
					try {
						singleThreadExecutor.submit(runnable).get();
					} catch (InterruptedException | ExecutionException ignored) {
					}
				}
			}.queue();
		}
	}

	/**
	 * Copy-pasted from {@code UnscrambleDialog#normalizeText(String)}
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
				if (lastLine.toString().matches("\\s*at") && !line.matches("\\s+.*")) {
					builder.append(" "); // separate 'at' from file name
				}
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

			while ((0 < len) && (line.charAt(len - 1) <= ' ')) {
				len--;
			}
			return (len < line.length()) ? line.substring(0, len) : line;
		}
	}


	/**
	 * Can't use {@code FileTypeManager.getInstance().getFileTypeByExtension(".kts");} here
	 * because it will return FileType for .kt files and this will cause creating files with wrong extension.
	 */
	public static class KotlinScriptFileType implements FileType {
		public static final KotlinScriptFileType INSTANCE = new KotlinScriptFileType();
		private final NotNullLazyValue<Icon> myIcon = new NotNullLazyValue<Icon>() {
			@NotNull
			protected Icon compute() {
				return IconLoader.getIcon("/org/jetbrains/kotlin/idea/icons/kotlin_file.png");
			}
		};

		@NotNull
		public String getName() {
			return "Kotlin";
		}

		@NotNull
		public String getDescription() {
			return this.getName();
		}

		@NotNull
		public String getDefaultExtension() {
			return "kts";
		}

		public Icon getIcon() {
			return myIcon.getValue();
		}

		@Override public boolean isBinary() {
			return false;
		}

		@Override public boolean isReadOnly() {
			return false;
		}

		@Nullable @Override public String getCharset(@NotNull VirtualFile virtualFile, @NotNull byte[] bytes) {
			return null;
		}
	}
}
