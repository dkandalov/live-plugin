package liveplugin.pluginrunner

import com.intellij.openapi.util.io.FileUtil
import org.junit.Test

class GroovyPluginRunnerTest {
	private static final LinkedHashMap NO_BINDING = [:]
	private static final LinkedHashMap NO_ENVIRONMENT = [:]

	private ErrorReporter errorReporter = new ErrorReporter()
	private GroovyPluginRunner pluginRunner = new GroovyPluginRunner(errorReporter, NO_ENVIRONMENT)

	@Test void "should run correct groovy script without errors"() {
		def scriptCode = """
			// import to ensure that groovy has access to parent classloader from which test is run
			import com.intellij.openapi.util.io.FileUtil

			// some groovy code
			def a = 1
			def b = 2
			a + b
"""
		givenScriptWith(scriptCode) { File file ->
			pluginRunner.runGroovyScript(file.absolutePath, file.parentFile.absolutePath, "someId", NO_BINDING)

			assert collectErrorsFrom(errorReporter).empty
		}
	}

	@Test void "should run incorrect groovy script with errors"() {
		def scriptCode = """
			invalid code + 1
"""
		givenScriptWith(scriptCode) { File file ->
			pluginRunner.runGroovyScript(file.absolutePath, file.parentFile.absolutePath, "someId", NO_BINDING)

			def errors = collectErrorsFrom(errorReporter)
			assert errors.size() == 1
			assert errors.first()[0] == "someId"
			assert errors.first()[1].startsWith("groovy.lang.MissingPropertyException")
		}
	}

	private static givenScriptWith(String sourceCode, Closure callback) {
		def file = FileUtil.createTempFile("", "")
		file.write(sourceCode)
		try {
			callback(file)
		} finally {
			file.delete()
		}
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
