package liveplugin.pluginrunner

import com.intellij.openapi.util.io.FileUtil
import org.junit.After
import org.junit.Before
import org.junit.Test

class GroovyPluginRunnerTest {
	private static final LinkedHashMap NO_BINDING = [:]
	private static final LinkedHashMap NO_ENVIRONMENT = [:]

	private ErrorReporter errorReporter = new ErrorReporter()
	private GroovyPluginRunner pluginRunner = new GroovyPluginRunner(errorReporter, NO_ENVIRONMENT)
	private File directory

	@Test void "should run correct groovy script without errors"() {
		def scriptCode = """
			// import to ensure that groovy has access to parent classloader from which test is run
			import com.intellij.openapi.util.io.FileUtil

			// some groovy code
			def a = 1
			def b = 2
			a + b
"""
		def file = createFile("plugin.groovy", scriptCode)
		pluginRunner.runGroovyScript(file.absolutePath, file.parentFile.absolutePath, "someId", NO_BINDING)

		assert collectErrorsFrom(errorReporter).empty
	}

	@Test void "should run incorrect groovy script with errors"() {
		def scriptCode = """
			invalid code + 1
"""
		def file = createFile("plugin.groovy", scriptCode)
		pluginRunner.runGroovyScript(file.absolutePath, file.parentFile.absolutePath, "someId", NO_BINDING)

		def errors = collectErrorsFrom(errorReporter)
		assert errors.size() == 1
		assert errors.first()[0] == "someId"
		assert errors.first()[1].startsWith("groovy.lang.MissingPropertyException")
	}

	@Test void "should run groovy script which uses groovy class from another file"() {
		def scriptCode = """
			Util.myFunction()
"""
		def scriptCode2 = """
			class Util {
				static myFunction() { 42 }
			}
"""
		def file = createFile("plugin.groovy", scriptCode)
		createFile("Util.groovy", scriptCode2)

		pluginRunner.runGroovyScript(file.absolutePath, directory.absolutePath, "someId", NO_BINDING)

		assert collectErrorsFrom(errorReporter).empty
	}

	@Before void setup() {
		directory = FileUtil.createTempDirectory("", "")
	}

	@After void teardown() {
		directory.delete()
	}

	def createFile(String fileName, String fileContent) {
		def file = new File(directory, fileName)
		file.write(fileContent)
		file
	}

	private def static collectErrorsFrom(ErrorReporter errorReporter) {
		def errors = []
		errorReporter.reportLoadingErrors(new ErrorReporter.Callback() {
			@Override void display(String title, String message) {
				errors << [title, message]
			}
		})
		errorReporter.reportRunningPluginExceptions(new ErrorReporter.Callback() {
			@Override void display(String title, String message) {
				errors << [title, message]
			}
		})
		errors
	}
}
