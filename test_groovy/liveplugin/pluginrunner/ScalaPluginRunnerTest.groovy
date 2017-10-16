package liveplugin.pluginrunner
import com.intellij.openapi.util.io.FileUtil
import org.junit.After
import org.junit.Before
import org.junit.Test

import static liveplugin.pluginrunner.GroovyPluginRunnerTest.*

class ScalaPluginRunnerTest {
	private final ErrorReporter errorReporter = new ErrorReporter()
	private final ScalaPluginRunner pluginRunner = new ScalaPluginRunner(errorReporter, emptyEnvironment)
	private File rootFolder

	@Test void "run correct scala script without errors"() {
		def scriptCode = """
			// import to ensure that script has access to parent classloader from which test is run
			import com.intellij.openapi.util.io.FileUtil

			// some scala code
			val a: Int = 1
			val b: Int = 2
			a + b
		"""
		createFile("plugin.scala", scriptCode, rootFolder)
		pluginRunner.runPlugin(rootFolder.absolutePath, "someId", noBindings, runOnTheSameThread)

		assert collectErrorsFrom(errorReporter).empty
	}

	@Test void "run incorrect scala script reporting errors"() {
		def scriptCode = """
			this is not proper scala code
		"""
		createFile("plugin.scala", scriptCode, rootFolder)
		pluginRunner.runPlugin(rootFolder.absolutePath, "someId", noBindings, runOnTheSameThread)

		def errors = collectErrorsFrom(errorReporter)
		assert errors.size() == 1
		errors.first().with{
			assert it[0] == "someId"
			assert it[1].contains("error: value is is not a member of object")
		}
	}

	@Before void setup() {
		rootFolder = FileUtil.createTempDirectory("", "")
	}

	@After void teardown() {
		FileUtil.delete(rootFolder)
	}
}
