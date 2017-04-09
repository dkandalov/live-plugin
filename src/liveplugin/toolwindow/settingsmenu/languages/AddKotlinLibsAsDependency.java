package liveplugin.toolwindow.settingsmenu.languages;

import com.intellij.ide.ui.laf.IntelliJLaf;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import liveplugin.toolwindow.util.DependenciesUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.util.Pair.create;
import static com.intellij.util.containers.ContainerUtil.map;
import static liveplugin.LivePluginAppComponent.LIVEPLUGIN_LIBS_PATH;
import static liveplugin.LivePluginAppComponent.kotlinCompilerIsOnClassPath;
import static liveplugin.MyFileUtil.fileNamesMatching;

public class AddKotlinLibsAsDependency extends AnAction implements DumbAware {
	private static final String LIBRARY_NAME = "LivePlugin - Kotlin";

	@Override public void update(@NotNull AnActionEvent event) {
		Project project = event.getProject();
		if (project == null) return;

		if (DependenciesUtil.anyModuleHasLibraryAsDependencyIn(project, LIBRARY_NAME)) {
			event.getPresentation().setText("Remove Kotlin Libraries from Project");
			event.getPresentation().setDescription("Remove Kotlin Libraries from Project");
		} else {
			event.getPresentation().setText("Add Kotlin Libraries to Project");
			event.getPresentation().setDescription("Add Kotlin Libraries to Project");
			event.getPresentation().setEnabled(kotlinCompilerIsOnClassPath());
		}
	}

	@Override public void actionPerformed(@NotNull AnActionEvent event) {
		Project project = event.getProject();
		if (project == null) return;

		if (DependenciesUtil.anyModuleHasLibraryAsDependencyIn(project, LIBRARY_NAME)) {
			DependenciesUtil.removeLibraryDependencyFrom(project, LIBRARY_NAME);
		} else {
			List<Pair<String, OrderRootType>> paths1 = map(
					fileNamesMatching(DownloadKotlinCompilerLib.LIB_FILES_PATTERN, LIVEPLUGIN_LIBS_PATH),
					fileName -> create("jar://" + LIVEPLUGIN_LIBS_PATH + fileName + "!/", CLASSES)
			);
			String ideaJarPath = PathManager.getJarPathForClass(IntelliJLaf.class);
			assert ideaJarPath != null;
			String ideLibsFolder = new File(ideaJarPath).getParentFile().getAbsolutePath() + "/";
			List<Pair<String, OrderRootType>> paths2 = map(
					fileNamesMatching("kotlin-.*jar", ideLibsFolder),
					fileName -> create("jar://" + ideLibsFolder + fileName + "!/", CLASSES)
			);
			DependenciesUtil.addLibraryDependencyTo(project, LIBRARY_NAME, ContainerUtil.concat(paths1, paths2));
		}
	}
}
