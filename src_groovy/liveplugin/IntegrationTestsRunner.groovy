package liveplugin

import com.intellij.openapi.project.Project
import org.junit.Ignore
import org.junit.Test

@SuppressWarnings(["GroovyUnusedDeclaration"])
class IntegrationTestsRunner {
	static def runIntegrationTests(Project project, List<Class> testClasses) {
		def result = testClasses.collect{ runTestsInClass(it) }.join("\n\n")
		PluginUtil.showInConsole(result, "Integration tests", project)
	}

	// TODO use standard intellij unit test window

	private static String runTestsInClass(Class testClass) {
		def testMethods = testClass.declaredMethods.findAll{
			it.annotations.find{ it instanceof Test } != null && it.annotations.find{ it instanceof Ignore } == null
		}

		testMethods.collect{ method ->
			testClass.simpleName + ": " + runTest(method.name){ method.invoke(testClass.newInstance()) }
		}.join("\n")
	}

	private static String runTest(String methodName, Closure closure) {
		try {

			closure()
			methodName + " - OK"

		} catch (AssertionError assertionError) {
			def writer = new StringWriter()
			assertionError.printStackTrace(new PrintWriter(writer))
			methodName + " - FAILED \n" + writer.buffer.toString()
		}
	}
}
