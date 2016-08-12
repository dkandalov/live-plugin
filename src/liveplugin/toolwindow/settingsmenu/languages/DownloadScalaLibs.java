package liveplugin.toolwindow.settingsmenu.languages;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.map;
import static java.util.Arrays.asList;
import static liveplugin.IDEUtil.askIfUserWantsToRestartIde;
import static liveplugin.IDEUtil.downloadFiles;
import static liveplugin.LivePluginAppComponent.*;
import static liveplugin.MyFileUtil.fileNamesMatching;

public class DownloadScalaLibs extends AnAction implements DumbAware {
	public static final String LIB_FILES_PATTERN = "(scala-|scalap).*jar";
	private static final String APPROXIMATE_SIZE = "(~26Mb)";

	@Override public void actionPerformed(@NotNull AnActionEvent event) {
		if (scalaIsOnClassPath()) {
			int answer = Messages.showYesNoDialog(event.getProject(),
					"Do you want to remove Scala libraries from LivePlugin classpath? This action cannot be undone.", "Live Plugin", null);
			if (answer == Messages.YES) {
				for (String fileName : fileNamesMatching(LIB_FILES_PATTERN, LIVEPLUGIN_LIBS_PATH)) {
					FileUtil.delete(new File(LIVEPLUGIN_LIBS_PATH + fileName));
				}
				askIfUserWantsToRestartIde("For Scala libraries to be unloaded IDE restart is required. Restart now?");
			}
		} else {
			int answer = Messages.showOkCancelDialog(event.getProject(),
					"Scala libraries " + APPROXIMATE_SIZE + " will be downloaded to '" + LIVEPLUGIN_LIBS_PATH + "'." +
					"\n(If you already have scala >= 2.11, you can copy it manually and restart IDE.)", "Live Plugin", null);
			if (answer != Messages.OK) return;

			List<String> scalaLibs = asList("scala-library", "scala-compiler", "scala-reflect", "scalap");
			List<Pair<String, String>> urlAndFileNamePairs = map(scalaLibs, it -> {
				// Using alternative maven repo instead of "repo1.maven.org" because standard repo for some reason
				// returns 403 when requested scala libs from IntelliJ downloader (even though the same code works for clojure libs)
				// (using this particular repo because it seems to be the fastest mirror http://docs.codehaus.org/display/MAVENUSER/Mirrors+Repositories)
				return Pair.create("http://maven.antelink.com/content/repositories/central/org/scala-lang/" + it + "/2.11.7/", it + "-2.11.7.jar");
			});

			boolean downloaded = downloadFiles(urlAndFileNamePairs, LIVEPLUGIN_LIBS_PATH);
			if (downloaded) {
				askIfUserWantsToRestartIde("For Scala libraries to be loaded IDE restart is required. Restart now?");
			} else {
				livePluginNotificationGroup
						.createNotification("Failed to download Scala libraries", NotificationType.WARNING);
			}
		}
	}

	@Override public void update(@NotNull AnActionEvent event) {
		if (scalaIsOnClassPath()) {
			event.getPresentation().setText("Remove Scala from LivePlugin Classpath");
			event.getPresentation().setDescription("Remove Scala from LivePlugin Classpath");
		} else {
			event.getPresentation().setText("Download Scala to LivePlugin Classpath");
			event.getPresentation().setDescription("Download Scala libraries to LivePlugin classpath to enable scala plugins support " + APPROXIMATE_SIZE);
		}
	}
}
