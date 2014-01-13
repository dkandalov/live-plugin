package liveplugin.testrunner

import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.project.Project
import liveplugin.PluginUtil
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

class IntegrationTestsRunner implements ApplicationComponent {
	private static runTests = IntegrationTestsTextRunner.&runIntegrationTests

	/**
	 * Runs tests in specified {@code testClasses} reporting results using JUnit panel (for IDEs with JUnit and Java support)
	 * or in text console (for other IDEs).
	 *
	 * @param testClasses classes with tests. Note that this is not standard JUnit runner and it only supports
	 *                    {@link org.junit.Test} and {@link org.junit.Ignore} annotations.
	 *                    (New class instance is created for each test method.)
	 * @param project current project
	 * @param pluginPath (optional)
	 * @return
	 */
	static void runIntegrationTests(List<Class> testClasses, @NotNull Project project, @Nullable String pluginPath = null) {
		runTests(testClasses, project, pluginPath)
	}

	@Override void initComponent() {
		runTests = IntegrationTestsUIRunner.&runIntegrationTests
		PluginUtil.log("junit found")
	}

	@Override void disposeComponent() {}

	@Override String getComponentName() { this.class.simpleName }
}
