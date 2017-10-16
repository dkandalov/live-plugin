import liveplugin.testrunner.IntegrationTestsRunner
import org.junit.Test

IntegrationTestsRunner.runIntegrationTests([TestClass], project, pluginPath)

class TestClass {
	@Test void "failing test"() {
		assert 1 == 2
	}
}
