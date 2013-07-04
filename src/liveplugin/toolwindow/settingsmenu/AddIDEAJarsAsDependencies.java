package liveplugin.toolwindow.settingsmenu;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import liveplugin.toolwindow.util.DependenciesUtil;

import java.util.Arrays;
import java.util.List;

import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.util.Pair.create;
import static liveplugin.MyFileUtil.fileNamesMatching;

public class AddIDEAJarsAsDependencies extends AnAction {
	private static final String IDEA_JARS_LIBRARY = "IDEA jars";

	@Override public void actionPerformed(AnActionEvent event) {
		Project project = event.getProject();
		if (project == null) return;

		if (DependenciesUtil.allModulesHasLibraryAsDependencyIn(project, IDEA_JARS_LIBRARY)) {
			DependenciesUtil.removeLibraryDependencyFrom(project, IDEA_JARS_LIBRARY);
		} else {
			String ideaJarsPath = PathManager.getHomePath() + "/lib/";
			//noinspection unchecked
			DependenciesUtil.addLibraryDependencyTo(project, IDEA_JARS_LIBRARY, Arrays.asList(
					create("jar://" + ideaJarsPath + "openapi.jar!/", CLASSES),
					create("jar://" + ideaJarsPath + "idea.jar!/", CLASSES),
					create("jar://" + ideaJarsPath + "idea_rt.jar!/", CLASSES),
					create("jar://" + ideaJarsPath + "annotations.jar!/", CLASSES),
					create("jar://" + ideaJarsPath + "util.jar!/", CLASSES),
					create("jar://" + ideaJarsPath + "extensions.jar!/", CLASSES),
					create("jar://" + ideaJarsPath + findGroovyJarOn(ideaJarsPath) + "!/", CLASSES)
			));
		}
	}

	private static String findGroovyJarOn(String ideaJarsPath) {
		List<String> files = fileNamesMatching("groovy-all-.*jar", ideaJarsPath);
		if (files.isEmpty()) return "could-not-find-groovy.jar";
		else return files.get(0);
	}

	@Override public void update(AnActionEvent event) {
		Project project = event.getProject();
		if (project == null) return;

		if (DependenciesUtil.allModulesHasLibraryAsDependencyIn(project, IDEA_JARS_LIBRARY)) {
			event.getPresentation().setText("Remove IDEA Jars from Project");
			event.getPresentation().setDescription("Remove IDEA jars dependencies from project");
		} else {
			event.getPresentation().setText("Add IDEA Jars to Project");
			event.getPresentation().setDescription("Add IDEA jars to project as dependencies");
		}
	}
}
