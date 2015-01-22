package liveplugin.toolwindow.settingsmenu.languages;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static java.util.Arrays.asList;
import static liveplugin.IDEUtil.askIsUserWantsToRestartIde;
import static liveplugin.IDEUtil.downloadFiles;
import static liveplugin.LivePluginAppComponent.*;
import static liveplugin.MyFileUtil.fileNamesMatching;

public class DownloadClojureLibs extends AnAction {
	public static final String LIB_FILES_PATTERN = "clojure-.*jar";
	private static final String APPROXIMATE_SIZE = "(~5Mb)";

	@Override public void actionPerformed(@NotNull AnActionEvent event) {
		if (clojureIsOnClassPath()) {
			int answer = Messages.showYesNoDialog(event.getProject(),
					"Do you want to remove Clojure libraries from LivePlugin classpath? This action cannot be undone.", "Live Plugin", null);
			if (answer == Messages.YES) {
				for (String fileName : fileNamesMatching(LIB_FILES_PATTERN, LIVEPLUGIN_LIBS_PATH)) {
					FileUtil.delete(new File(LIVEPLUGIN_LIBS_PATH + fileName));
				}
				askIsUserWantsToRestartIde("For Clojure libraries to be unloaded IDE restart is required. Restart now?");
			}
		} else {
			int answer = Messages.showOkCancelDialog(event.getProject(),
					"Clojure libraries " + APPROXIMATE_SIZE + " will be downloaded to '" + LIVEPLUGIN_LIBS_PATH + "'." +
					"\n(If you already have clojure >= 1.5.1, you can copy it manually and restart IDE.)", "Live Plugin", null);
			if (answer != Messages.OK) return;

			@SuppressWarnings("unchecked")
			boolean downloaded = downloadFiles(asList(
					Pair.create("http://repo1.maven.org/maven2/org/clojure/clojure/1.5.1/", "clojure-1.5.1.jar"),
					Pair.create("http://repo1.maven.org/maven2/org/clojure/clojure-contrib/1.2.0/", "clojure-contrib-1.2.0.jar")
			), LIVEPLUGIN_LIBS_PATH);
			if (downloaded) {
				askIsUserWantsToRestartIde("For Clojure libraries to be loaded IDE restart is required. Restart now?");
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
			event.getPresentation().setDescription("Download Clojure libraries to LivePlugin classpath to enable clojure plugins support " + APPROXIMATE_SIZE);
		}
	}
}
