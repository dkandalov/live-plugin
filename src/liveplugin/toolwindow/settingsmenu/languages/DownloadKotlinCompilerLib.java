package liveplugin.toolwindow.settingsmenu.languages;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import kotlin.reflect.jvm.internal.impl.config.KotlinCompilerVersion;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static java.util.Arrays.asList;
import static liveplugin.IDEUtil.askIfUserWantsToRestartIde;
import static liveplugin.IDEUtil.downloadFiles;
import static liveplugin.LivePluginAppComponent.*;
import static liveplugin.MyFileUtil.fileNamesMatching;

public class DownloadKotlinCompilerLib extends AnAction {
	public static final String LIB_FILES_PATTERN = "kotlin-compiler.*jar";
	private static final String APPROXIMATE_SIZE = "(~25Mb)";

	@Override public void update(@NotNull AnActionEvent event) {
		boolean kotlinCompilerIsDownloaded = kotlinCompilerIsOnClassPath(); // TODO check if file exists
		if (kotlinCompilerIsDownloaded) {
			event.getPresentation().setText("Remove Kotlin from LivePlugin Classpath");
			event.getPresentation().setDescription("Remove Kotlin from LivePlugin Classpath");
		} else {
			event.getPresentation().setText("Download Kotlin to LivePlugin Classpath");
			event.getPresentation().setDescription("Download Kotlin compiler to LivePlugin classpath to enable Kotlin plugins support " + APPROXIMATE_SIZE);
		}
	}

	@Override public void actionPerformed(@NotNull AnActionEvent event) {
		if (kotlinCompilerIsOnClassPath()) {
			int answer = Messages.showYesNoDialog(event.getProject(),
					"Do you want to remove Kotlin compiler from LivePlugin classpath? This action cannot be undone.", "Live Plugin", null);
			if (answer == Messages.YES) {
				for (String fileName : fileNamesMatching(LIB_FILES_PATTERN, LIVEPLUGIN_LIBS_PATH)) {
					FileUtil.delete(new File(LIVEPLUGIN_LIBS_PATH + fileName));
				}
				askIfUserWantsToRestartIde("For Kotlin compiler to be unloaded IDE restart is required. Restart now?");
			}
		} else {
			int answer = Messages.showOkCancelDialog(
					event.getProject(),
					"Kotlin compiler " + APPROXIMATE_SIZE + " will be downloaded to '" + LIVEPLUGIN_LIBS_PATH + "'.\n", "Live Plugin",
					null
			);
			if (answer != Messages.OK) return;

			Pair<String, String> urlAndFileName = Pair.create(
					"http://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-compiler-embeddable/" + KotlinCompilerVersion.VERSION + "/",
					"kotlin-compiler-embeddable-" + KotlinCompilerVersion.VERSION + ".jar"
			);
			boolean downloaded = downloadFiles(asList(urlAndFileName), LIVEPLUGIN_LIBS_PATH);

			if (downloaded) askIfUserWantsToRestartIde("For Kotlin compiler to be loaded IDE restart is required. Restart now?");
			else livePluginNotificationGroup.createNotification("Failed to download Kotlin compiler", NotificationType.WARNING);
		}
	}
}
