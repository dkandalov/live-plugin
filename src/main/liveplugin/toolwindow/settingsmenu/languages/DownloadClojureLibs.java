package liveplugin.toolwindow.settingsmenu.languages;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static java.util.Arrays.asList;
import static liveplugin.IDEUtil.askIfUserWantsToRestartIde;
import static liveplugin.IDEUtil.downloadFiles;
import static liveplugin.LivePluginAppComponent.*;
import static liveplugin.MyFileUtil.fileNamesMatching;

public class DownloadClojureLibs extends AnAction implements DumbAware {
	public static final String libFilesPattern = "clojure-.*jar";
	private static final String approximateSize = "(~5Mb)";

	@Override public void actionPerformed(@NotNull AnActionEvent event) {
		if (clojureIsOnClassPath()) {
			int answer = Messages.showYesNoDialog(event.getProject(),
					"Do you want to remove Clojure libraries from LivePlugin classpath? This action cannot be undone.", "Live Plugin", null);
			if (answer == Messages.YES) {
				for (String fileName : fileNamesMatching(libFilesPattern, livepluginLibsPath)) {
					FileUtil.delete(new File(livepluginLibsPath + fileName));
				}
				askIfUserWantsToRestartIde("For Clojure libraries to be unloaded IDE restart is required. Restart now?");
			}
		} else {
			int answer = Messages.showOkCancelDialog(event.getProject(),
					"Clojure libraries " + approximateSize + " will be downloaded to '" + livepluginLibsPath + "'." +
					"\n(If you already have clojure >= 1.7.0, you can copy it manually and restart IDE.)", "Live Plugin", null);
			if (answer != Messages.OK) return;

			@SuppressWarnings("unchecked")
			boolean downloaded = downloadFiles(asList(
					Pair.create("http://repo1.maven.org/maven2/org/clojure/clojure/1.7.0/", "clojure-1.7.0.jar"),
					Pair.create("http://repo1.maven.org/maven2/org/clojure/clojure-contrib/1.2.0/", "clojure-contrib-1.2.0.jar")
			), livepluginLibsPath);
			if (downloaded) {
				askIfUserWantsToRestartIde("For Clojure libraries to be loaded IDE restart is required. Restart now?");
			} else {
				livePluginNotificationGroup
						.createNotification("Failed to download Clojure libraries", NotificationType.WARNING);
			}
		}
	}

	@Override public void update(@NotNull AnActionEvent event) {
		if (clojureIsOnClassPath()) {
			event.getPresentation().setText("Remove Clojure from LivePlugin Classpath");
			event.getPresentation().setDescription("Remove Clojure from LivePlugin Classpath");
		} else {
			event.getPresentation().setText("Download Clojure to LivePlugin Classpath");
			event.getPresentation().setDescription("Download Clojure libraries to LivePlugin classpath to enable clojure plugins support " + approximateSize);
		}
	}
}
