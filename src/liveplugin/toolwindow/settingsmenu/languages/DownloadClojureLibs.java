package liveplugin.toolwindow.settingsmenu.languages;

import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;

import static liveplugin.IdeUtil.askUserIfShouldRestartIde;
import static liveplugin.IdeUtil.downloadFile;
import static liveplugin.LivePluginAppComponent.LIVEPLUGIN_LIBS_PATH;
import static liveplugin.LivePluginAppComponent.clojureIsOnClassPath;
import static liveplugin.MyFileUtil.fileNamesMatching;

public class DownloadClojureLibs extends AnAction {
	public static final String LIB_FILES_PATTERN = "clojure-.*jar";
	private static final String APPROXIMATE_SIZE = "(~4Mb)";

	@Override public void actionPerformed(AnActionEvent event) {
		if (clojureIsOnClassPath()) {
			int answer = Messages.showYesNoDialog(event.getProject(),
					"Do you want to remove Clojure libraries from plugin classpath? This action cannot be undone.", "Live Plugin", null);
			if (answer == Messages.YES) {
				for (String fileName : fileNamesMatching(LIB_FILES_PATTERN, LIVEPLUGIN_LIBS_PATH)) {
					FileUtil.delete(new File(LIVEPLUGIN_LIBS_PATH + fileName));
				}
				askUserIfShouldRestartIde();
			}
		} else {
			int answer = Messages.showOkCancelDialog(event.getProject(),
					"Clojure libraries " + APPROXIMATE_SIZE + " will be downloaded to '" + LIVEPLUGIN_LIBS_PATH + "'." +
					"\n(If you already have clojure >= 1.5.1, you can copy it manually and restart IDE.)", "Live Plugin", null);
			if (answer != Messages.OK) return;

			boolean downloaded = downloadFile("http://repo1.maven.org/maven2/org/clojure/clojure/1.5.1/", "clojure-1.5.1.jar", LIVEPLUGIN_LIBS_PATH);
			if (downloaded) {
				askUserIfShouldRestartIde(); // TODO load classes at runtime
			} else {
				NotificationGroup.balloonGroup("Live Plugin")
						.createNotification("Failed to download Clojure libraries", NotificationType.WARNING);
			}
		}
	}

	@Override public void update(AnActionEvent event) {
		if (clojureIsOnClassPath()) {
			event.getPresentation().setText("Remove Clojure from Plugin Classpath");
			event.getPresentation().setDescription("Remove Clojure from Plugin Classpath");
		} else {
			event.getPresentation().setText("Download Clojure to Plugin Classpath");
			event.getPresentation().setDescription("Download Clojure libraries to plugin classpath to enable clojure plugins support " + APPROXIMATE_SIZE);
		}
	}
}
