package liveplugin

import com.intellij.openapi.project.Project
import org.junit.Ignore
import org.junit.Test

@SuppressWarnings(["GroovyUnusedDeclaration"])
class IntegrationTestsRunner {
	static def runIntegrationTests(Project project, List<Class> testClasses) {
		def result = testClasses.collect{ runTestsInClass(it, project) }.join("\n\n")
		PluginUtil.showInConsole(result, "Integration tests", project)
	}

	// TODO use standard intellij unit test window

	private static String runTestsInClass(Class testClass, Project project) {
		def testMethods = testClass.declaredMethods.findAll{
			it.annotations.find{ it instanceof Test } != null && it.annotations.find{ it instanceof Ignore } == null
		}

		testMethods.collect{ method ->
			testClass.simpleName + ": " + runTest(method.name){
				method.invoke(createInstanceOf(testClass, project))
			}
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

	private static Object createInstanceOf(Class testClass, Project project) {
		def hasDefaultConstructor = testClass.constructors.any{ it.parameterTypes.size() == 0 }
		if (hasDefaultConstructor) return testClass.newInstance()

		def hasConstructorWithProject = testClass.constructors.any{
			it.parameterTypes.size() == 1 && it.parameterTypes.first() == Project
		}
		if (hasConstructorWithProject) return testClass.newInstance(project)

		throw new IllegalStateException("Failed to create test class ${testClass}")
	}
}
