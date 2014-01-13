package liveplugin.testrunner
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import static liveplugin.testrunner.IntegrationTestsRunner.runTestsInClass

@SuppressWarnings(["GroovyUnusedDeclaration"])
class IntegrationTestsUIRunner {
	static void runIntegrationTests(List<Class> testClasses, @NotNull Project project, @Nullable String pluginPath = null) {
		def context = [project: project, pluginPath: pluginPath]
		def now = System.currentTimeMillis()

		def jUnitPanel = new JUnitPanel().showIn(project)
		jUnitPanel.startedAllTests(now)
		testClasses.collect{ runTestsInClass(it, context, jUnitPanel, now) }
		jUnitPanel.finishedAllTests(now)
	}
}
