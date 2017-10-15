package liveplugin.pluginrunner
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.Function
import org.junit.After
import org.junit.Before
import org.junit.Test

import static liveplugin.pluginrunner.GroovyPluginRunner.mainScript

class GroovyPluginRunnerTest {
	static final LinkedHashMap NO_BINDING = [:]
	static final LinkedHashMap NO_ENVIRONMENT = [:]
	static final Function RUN_ON_THE_SAME_THREAD = new Function<Runnable, Void>() {
		@Override Void fun(Runnable runnable) {
			runnable.run()
			null
		}
	}

	private final ErrorReporter errorReporter = new ErrorReporter()
	private final GroovyPluginRunner pluginRunner = new GroovyPluginRunner(mainScript, errorReporter, NO_ENVIRONMENT)
	private File rootFolder
	private File myPackageFolder


	@Test void "run correct groovy script without errors"() {
		def scriptCode = """
			// import to ensure that script has access to parent classloader from which test is run
			import com.intellij.openapi.util.io.FileUtil

			// some groovy code
			def a = 1
			def b = 2
			a + b
		"""
		createFile("plugin.groovy", scriptCode, rootFolder)
		pluginRunner.runPlugin(rootFolder.absolutePath, "someId", NO_BINDING, RUN_ON_THE_SAME_THREAD)

		assert collectErrorsFrom(errorReporter).empty
	}

	@Test void "run incorrect groovy script with errors"() {
		def scriptCode = """
			invalid code + 1
		"""
		createFile("plugin.groovy", scriptCode, rootFolder)
		pluginRunner.runPlugin(rootFolder.absolutePath, "someId", NO_BINDING, RUN_ON_THE_SAME_THREAD)

		def errors = collectErrorsFrom(errorReporter)
		assert errors.size() == 1
		errors.first().with{
			assert it[0] == "someId"
			assert it[1].startsWith("groovy.lang.MissingPropertyException")
		}
	}

	@Test void "run groovy script which uses groovy class from another file"() {
		def scriptCode = """
			import myPackage.Util
			Util.myFunction()
		"""
		def scriptCode2 = """
			package myPackage
			class Util {
				static myFunction() { 42 }
			}
		"""
		createFile("plugin.groovy", scriptCode, rootFolder)
		createFile("Util.groovy", scriptCode2, myPackageFolder)

		pluginRunner.runPlugin(rootFolder.absolutePath, "someId", NO_BINDING, RUN_ON_THE_SAME_THREAD)

		assert collectErrorsFrom(errorReporter).empty
	}

	@Before void setup() {
		rootFolder = FileUtil.createTempDirectory("", "")
		myPackageFolder = new File(rootFolder, "myPackage")
		myPackageFolder.mkdir()
	}

	@After void teardown() {
		FileUtil.delete(rootFolder)
	}

	static createFile(String fileName, String fileContent, File directory) {
		def file = new File(directory, fileName)
		file.write(fileContent)
		file
	}

	static collectErrorsFrom(ErrorReporter errorReporter) {
		def errors = []
		errorReporter.reportAllErrors(new ErrorReporter.Callback() {
			@Override void display(String title, String message) {
				errors << [title, message]
			}
		})
		errors
	}
}
