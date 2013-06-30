package liveplugin.pluginrunner

import com.intellij.openapi.util.io.FileUtil
import org.junit.After
import org.junit.Before
import org.junit.Test

import static liveplugin.MyFileUtil.asUrl
import static liveplugin.pluginrunner.GroovyPluginRunnerTest.*

class ScalaPluginRunnerTest {
	private static final LinkedHashMap NO_BINDING = [:]
	private static final LinkedHashMap NO_ENVIRONMENT = [:]

	private final ErrorReporter errorReporter = new ErrorReporter()
	private final ScalaPluginRunner pluginRunner = new ScalaPluginRunner(errorReporter, NO_ENVIRONMENT)
	private File rootFolder

	@Test void "should run correct scala script without errors"() {
		def scriptCode = """
			// import to ensure that groovy has access to parent classloader from which test is run
			import com.intellij.openapi.util.io.FileUtil

			// some scala code
			val a: Int = 1
			val b: Int = 2
			a + b
		"""
		createFile("plugin.scala", scriptCode, rootFolder)
		pluginRunner.runPlugin(asUrl(rootFolder), "someId", NO_BINDING)

		assert collectErrorsFrom(errorReporter).empty
	}

	@Before void setup() {
		rootFolder = FileUtil.createTempDirectory("", "")
	}

	@After void teardown() {
		FileUtil.delete(rootFolder)
	}
}
