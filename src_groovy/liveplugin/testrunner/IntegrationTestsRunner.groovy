package liveplugin.testrunner

import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.project.Project
import liveplugin.PluginUtil
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

class IntegrationTestsRunner  implements ApplicationComponent {
	private static runTests = IntegrationTestsTextRunner.&runIntegrationTests

	static def runIntegrationTests(List<Class> testClasses, @NotNull Project project, @Nullable String pluginPath = null) {
		runTests(testClasses, project, pluginPath)
	}

	@Override void initComponent() {
		runTests = IntegrationTestsUIRunner.&runIntegrationTests
		PluginUtil.log("junit found")
	}

	@Override void disposeComponent() {}

	@Override String getComponentName() { this.class.simpleName }
}
