package liveplugin.testrunner

import com.intellij.openapi.project.Project
import liveplugin.PluginUtil
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.junit.Ignore
import org.junit.Test

@SuppressWarnings(["GroovyUnusedDeclaration"])
class IntegrationTestsTextRunner {
	static def runIntegrationTests(List<Class> testClasses, @NotNull Project project, @Nullable String pluginPath = null) {
		def context = [project: project, pluginPath: pluginPath]
		def result = testClasses.collect{ runTestsInClass(it, context) }.join("\n\n")
		PluginUtil.showInConsole(result, "Integration tests", project)
	}

	private static String runTestsInClass(Class testClass, Map context) {
		def testMethods = testClass.declaredMethods.findAll{
			it.annotations.find{ it instanceof Test } != null && it.annotations.find{ it instanceof Ignore } == null
		}

		testMethods.collect{ method ->
			testClass.simpleName + ": " + runTest(method.name){
				method.invoke(createInstanceOf(testClass, context))
			}
		}.join("\n")
	}

	private static String runTest(String methodName, Closure closure) {
		try {

			closure()
			"\"${methodName}\" - OK"

		} catch (AssertionError assertionError) {
			def writer = new StringWriter()
			assertionError.cause.printStackTrace(new PrintWriter(writer))
			methodName + " - FAILED \n" + writer.buffer.toString()
		}
	}

	static Object createInstanceOf(Class testClass, Map context) {
		def hasConstructorWithContext = testClass.constructors.any{
			it.parameterTypes.size() == 1 && it.parameterTypes.first() == Map
		}
		if (hasConstructorWithContext) return testClass.newInstance(context)

		def hasDefaultConstructor = testClass.constructors.any{ it.parameterTypes.size() == 0 }
		if (hasDefaultConstructor) return testClass.newInstance()

		throw new IllegalStateException("Failed to create test class ${testClass}")
	}
}
