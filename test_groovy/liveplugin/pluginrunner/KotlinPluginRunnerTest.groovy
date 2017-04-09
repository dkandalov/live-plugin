package liveplugin.pluginrunner

import com.intellij.openapi.util.io.FileUtil
import org.junit.After
import org.junit.Before
import org.junit.Test

import static liveplugin.pluginrunner.GroovyPluginRunnerTest.*

class KotlinPluginRunnerTest {
	private final ErrorReporter errorReporter = new ErrorReporter()
	private final pluginRunner = new KotlinPluginRunner(errorReporter, NO_ENVIRONMENT)
	private File rootFolder
	private File libPackageFolder

	@Test void "minimal kotlin script"() {
		def scriptCode = "println(123)"
		createFile("plugin.kts", scriptCode, rootFolder)

		pluginRunner.runPlugin(rootFolder.absolutePath, "someId", NO_BINDING, RUN_ON_THE_SAME_THREAD)

		assert collectErrorsFrom(errorReporter).empty
	}

	@Test void "kotlin script which uses IJ API"() {
		def scriptCode = "println(com.intellij.openapi.project.Project::class.java)"
		createFile("plugin.kts", scriptCode, rootFolder)

		pluginRunner.runPlugin(rootFolder.absolutePath, "someId", NO_BINDING, RUN_ON_THE_SAME_THREAD)

		assert collectErrorsFrom(errorReporter).empty
	}
	
	@Test void "kotlin script which uses function from another file"() {
		def scriptCode = """
			import lib.libFunction
			println(libFunction())
		"""
		def libScriptCode = """
			package lib
			fun libFunction(): Long = 42
		"""
		createFile("plugin.kts", scriptCode, rootFolder)
		createFile("lib.kt", libScriptCode, libPackageFolder)

		pluginRunner.runPlugin(rootFolder.absolutePath, "someId", NO_BINDING, RUN_ON_THE_SAME_THREAD)

		assert collectErrorsFrom(errorReporter).empty
	}

	@Test void "kotlin script with errors"() {
		def scriptCode = "abc"
		createFile("plugin.kts", scriptCode, rootFolder)

		pluginRunner.runPlugin(rootFolder.absolutePath, "someId", NO_BINDING, RUN_ON_THE_SAME_THREAD)

		def errors = collectErrorsFrom(errorReporter)
		assert errors.size == 1
		errors.first().with {
			assert it[0] == "Loading errors"
			assert it[1].contains("error: unresolved reference: abc")
		}
	}

	@Before void setup() {
		rootFolder = FileUtil.createTempDirectory("", "")
		libPackageFolder = new File(rootFolder, "lib")
		libPackageFolder.mkdir()
	}

	@After void teardown() {
		FileUtil.delete(rootFolder)
	}
}
