package liveplugin.toolwindow.settingsmenu.languages;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Pair;
import liveplugin.toolwindow.util.DependenciesUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.util.Pair.create;
import static com.intellij.util.containers.ContainerUtil.map;
import static liveplugin.LivePluginAppComponent.livepluginLibsPath;
import static liveplugin.LivePluginAppComponent.clojureIsOnClassPath;
import static liveplugin.MyFileUtil.fileNamesMatching;

public class AddClojureLibsAsDependency extends AnAction implements DumbAware {
	private static final String libraryName = "LivePlugin - Clojure";

	@Override public void actionPerformed(@NotNull AnActionEvent event) {
		Project project = event.getProject();
		if (project == null) return;

		if (DependenciesUtil.anyModuleHasLibraryAsDependencyIn(project, libraryName)) {
			DependenciesUtil.removeLibraryDependencyFrom(project, libraryName);
		} else {
			List<Pair<String, OrderRootType>> paths = map(fileNamesMatching(DownloadClojureLibs.libFilesPattern, livepluginLibsPath), fileName -> create("jar://" + livepluginLibsPath + fileName + "!/", CLASSES));
			DependenciesUtil.addLibraryDependencyTo(project, libraryName, paths);
		}
	}

	@Override public void update(@NotNull AnActionEvent event) {
		Project project = event.getProject();
		if (project == null) return;

		if (DependenciesUtil.anyModuleHasLibraryAsDependencyIn(project, libraryName)) {
			event.getPresentation().setText("Remove Clojure Libraries from Project");
			event.getPresentation().setDescription("Remove Clojure Libraries from Project");
		} else {
			event.getPresentation().setText("Add Clojure Libraries to Project");
			event.getPresentation().setDescription("Add Clojure Libraries to Project");
			event.getPresentation().setEnabled(clojureIsOnClassPath());
		}
	}
}
