import liveplugin.testrunner.IntegrationTestsRunner
import org.junit.Test

IntegrationTestsRunner.runIntegrationTests([TestClass], project, pluginPath)

class TestClass {
	@Test void "a test"() {
		assert 1 == 2
	}
}
