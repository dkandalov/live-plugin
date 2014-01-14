package liveplugin.testrunner

import com.intellij.openapi.project.Project
import liveplugin.PluginUtil
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

import static liveplugin.testrunner.IntegrationTestsRunner.runTestsInClass

@SuppressWarnings(["GroovyUnusedDeclaration"])
class IntegrationTestsTextRunner {
	static void runIntegrationTests(List<Class> testClasses, @NotNull Project project, @Nullable String pluginPath = null) {
		def context = [project: project, pluginPath: pluginPath]
		def now = System.currentTimeMillis()

		PluginUtil.doInBackground("Running integration tests", false) {
			def textReporter = new TextTestReporter()
			textReporter.startedAllTests(now)
			testClasses.collect{ runTestsInClass(it, context, textReporter, now) }
			textReporter.finishedAllTests(now)

			PluginUtil.showInConsole(textReporter.result, "Integration tests", project)
		}
	}

	private static class TextTestReporter implements TestReporter {
		String result = ""

		@Override void startedAllTests(long time) {}
		@Override void finishedAllTests(long time) {}
		@Override void running(String className, String methodName, long time) {}
		@Override void passed(String methodName, long time) {
			result += "\"${methodName}\" - OK\n"
		}
		@Override void failed(String methodName, String error, long time) {
			result += methodName + " - FAILED\n" + error + "\n"
		}
		@Override void error(String methodName, String error, long time) {
			result += methodName + " - ERROR\n" + error + "\n"
		}
		@Override void ignored(String methodName) {
			result += "\"${methodName}\" - IGNORED\n"
		}
		@Override void finishedClass(String className, long time) {}
	}
}
