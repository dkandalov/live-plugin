package liveplugin.toolwindow.settingsmenu;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.module.Module;
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
import static java.util.Arrays.asList;
import static liveplugin.LivePluginAppComponent.LIVEPLUGIN_LIBS_PATH;
import static liveplugin.MyFileUtil.fileNamesMatching;
import static liveplugin.toolwindow.util.DependenciesUtil.*;
import static com.intellij.util.containers.ContainerUtil.map;

public class EnableLivePluginAutoComplete {
	private static final String LIVE_PLUGIN_LIBRARY = "LivePlugin jars for auto-complete in scripts";

	public static void applyTo(Project project) {
		Runnable runnable = () -> {
			boolean enabled = isEnabledFor(project);
			Module module = findLivePluginModuleWithLibrary(project);
			boolean hasJars = module != null;

			if (!enabled && hasJars) removeLivePluginDependenciesFrom(project);
			else if (enabled && !hasJars) addLivePluginDependenciesTo(project);
		};
		ApplicationManager.getApplication().invokeAndWait(runnable, ModalityState.NON_MODAL);
	}

	private static boolean isEnabledFor(Project project) {
		return ServiceManager.getService(project, StateByProject.class).autoCompleteInScripts;
	}

	public static Module findLivePluginModuleWithLibrary(Project project) {
		return findModuleWithLibrary(project, LIVE_PLUGIN_LIBRARY);
	}

	private static void removeLivePluginDependenciesFrom(Project project) {
		removeLibraryDependencyFrom(project, LIVE_PLUGIN_LIBRARY);
	}

	private static void addLivePluginDependenciesTo(Project project) {
		List<Pair<String, OrderRootType>> paths = new ArrayList<>();
		paths.add(create("jar://" + getJarPathForClass(LivePluginAppComponent.class) + "!/src/", SOURCES));
		paths.addAll(map(
				fileNamesMatching(".*.jar", LIVEPLUGIN_LIBS_PATH),
				fileName -> create("jar://" + LIVEPLUGIN_LIBS_PATH + fileName + "!/", CLASSES)
		));

		String ideaJarsPath = PathManager.getHomePath() + "/lib/";
		paths.addAll(asList(
				create("jar://" + ideaJarsPath + "openapi.jar!/", CLASSES),
				create("jar://" + ideaJarsPath + "idea.jar!/", CLASSES),
				create("jar://" + ideaJarsPath + "idea_rt.jar!/", CLASSES),
				create("jar://" + ideaJarsPath + "annotations.jar!/", CLASSES),
				create("jar://" + ideaJarsPath + "util.jar!/", CLASSES),
				create("jar://" + ideaJarsPath + "extensions.jar!/", CLASSES),
				create("jar://" + ideaJarsPath + findGroovyJarOn(ideaJarsPath) + "!/", CLASSES)
		));

		addLibraryDependencyTo(project, LIVE_PLUGIN_LIBRARY, paths);
	}

	public static String findGroovyJarOn(String ideaJarsPath) {
		List<String> files = fileNamesMatching("groovy-all-.*jar", ideaJarsPath);
		if (files.isEmpty()) return "could-not-find-groovy.jar";
		else return files.get(0);
	}


	@State(name = "LivePlugin", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
	public static class StateByProject {
		boolean autoCompleteInScripts;
	}


	public static class Action extends AnAction implements DumbAware {
		@Override public void actionPerformed(AnActionEvent event) {
			Project project = event.getProject();
			if (project == null) return;
			StateByProject state = ServiceManager.getService(project, StateByProject.class);
			if (state == null) return;
			state.autoCompleteInScripts = !state.autoCompleteInScripts;

			applyTo(project);
		}

		@Override public void update(@NotNull AnActionEvent event) {
			Project project = event.getProject();
			if (project == null) return;
			StateByProject state = ServiceManager.getService(project, StateByProject.class);
			if (state == null) return;
			
			if (state.autoCompleteInScripts) {
				event.getPresentation().setText("Disable Code Completion in LivePlugins");
				event.getPresentation().setDescription(
						"Remove LivePlugin jars from project dependencies. " +
						"This will disable auto-complete and other IDE features in LivePlugins.");
			} else {
				event.getPresentation().setText("Enable Code Completion in LivePlugins");
				event.getPresentation().setDescription(
						"Add LivePlugin jars to project dependencies. " +
						"This will make auto-complete and other IDE features available in LivePlugins.");
			}
		}
	}
}