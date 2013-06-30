package liveplugin.pluginrunner

import com.intellij.openapi.util.io.FileUtil
import org.junit.After
import org.junit.Before
import org.junit.Test

import static liveplugin.MyFileUtil.asUrl
import static liveplugin.pluginrunner.GroovyPluginRunnerTest.collectErrorsFrom
import static liveplugin.pluginrunner.GroovyPluginRunnerTest.createFile

class ClojurePluginRunnerTest {
	private static final LinkedHashMap NO_BINDING = [:]
	private static final LinkedHashMap NO_ENVIRONMENT = [:]

	private final ErrorReporter errorReporter = new ErrorReporter()
	private final ClojurePluginRunner pluginRunner = new ClojurePluginRunner(errorReporter, NO_ENVIRONMENT)
	private File rootFolder

	@Test void "should run correct clojure script without errors"() {
		def scriptCode = """
			// import to ensure that script has access to parent classloader from which test is run
			(import com.intellij.openapi.util.io.FileUtil)

			// some clojure code
			(+ 1 2)
		"""
		createFile("plugin.clj", scriptCode, rootFolder)
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
