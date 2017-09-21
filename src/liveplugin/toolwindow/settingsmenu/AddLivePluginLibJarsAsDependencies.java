package liveplugin.toolwindow.settingsmenu;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Pair;
import liveplugin.LivePluginAppComponent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.roots.OrderRootType.SOURCES;
import static com.intellij.openapi.util.Pair.create;
import static com.intellij.util.PathUtil.getJarPathForClass;
import static liveplugin.LivePluginAppComponent.LIVEPLUGIN_LIBS_PATH;
import static liveplugin.MyFileUtil.fileNamesMatching;
import static liveplugin.toolwindow.util.DependenciesUtil.*;
import static org.jetbrains.kotlin.com.intellij.util.containers.ContainerUtil.map;

public class AddLivePluginLibJarsAsDependencies extends AnAction implements DumbAware {
	public static final String LIVE_PLUGIN_LIBRARY_OLD = "LivePlugin"; // TODO remove to migrate from older version of the plugins (do the same with IJ jars)

	@Override public void actionPerformed(@NotNull AnActionEvent event) {
		Project project = event.getProject();
		if (project == null) return;

		if (anyModuleHasLibraryAsDependencyIn(project, LIVE_PLUGIN_LIBRARY_OLD)) {
			removeLibraryDependencyFrom(project, LIVE_PLUGIN_LIBRARY_OLD);
		} else {
			addLivePluginDependenciesTo(project, LIVE_PLUGIN_LIBRARY_OLD);
		}
	}

	@Override public void update(@NotNull AnActionEvent event) {
		Project project = event.getProject();
		if (project == null) return;

		if (anyModuleHasLibraryAsDependencyIn(project, LIVE_PLUGIN_LIBRARY_OLD)) {
			event.getPresentation().setText("Remove LivePlugin Jars from Project");
			event.getPresentation().setDescription(
					"Remove LivePlugin jars from project dependencies. " +
					"This will enable auto-complete and other IDE features for IntelliJ classes.");
		} else {
			event.getPresentation().setText("Add LivePlugin Jars to Project");
			event.getPresentation().setDescription(
					"Add LivePlugin jars to project dependencies. " +
					"This will enable auto-complete and other IDE features.");
		}
	}

	public static void addLivePluginDependenciesTo(Project project, String libraryName) {
		List<Pair<String, OrderRootType>> paths = new ArrayList<>(map(
				fileNamesMatching(".*.jar", LIVEPLUGIN_LIBS_PATH),
				fileName -> create("jar://" + LIVEPLUGIN_LIBS_PATH + fileName + "!/", CLASSES)
		));
		paths.add(create("jar://" + getJarPathForClass(LivePluginAppComponent.class) + "!/src/", SOURCES));

		addLibraryDependencyTo(project, libraryName, paths);
	}
}
