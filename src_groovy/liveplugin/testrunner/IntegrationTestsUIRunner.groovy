package liveplugin.testrunner

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.junit.Ignore
import org.junit.Test

import java.lang.reflect.Method

import static IntegrationTestsTextRunner.createInstanceOf

@SuppressWarnings(["GroovyUnusedDeclaration"])
class IntegrationTestsUIRunner {
	static def runIntegrationTests(List<Class> testClasses, @NotNull Project project, @Nullable String pluginPath = null) {
		def context = [project: project, pluginPath: pluginPath]

		def jUnitPanel = new JUnitPanel().showIn(project)
		jUnitPanel.startedAllTests()
		testClasses.collect{ runTestsInClass(it, context, jUnitPanel) }
		jUnitPanel.finishedAllTests()
	}

	private static runTestsInClass(Class testClass, Map context, JUnitPanel jUnitPanel) {
		def isTest = { Method method -> method.annotations.find{ it instanceof Test} }
		def isIgnored = { Method method -> method.annotations.find{ it instanceof Ignore} }

		testClass.declaredMethods.findAll{ isTest(it) }.each{ method ->
			if (isIgnored(method))
				ignoreTest(testClass.name, method.name, jUnitPanel)
			else
				runTest(testClass.name, method.name, jUnitPanel) { method.invoke(createInstanceOf(testClass, context)) }
		}

		jUnitPanel.finishedClass(testClass.name)
	}

	private static ignoreTest(String className, String methodName, JUnitPanel jUnitPanel) {
		jUnitPanel.running(className, methodName)
		jUnitPanel.ignored(methodName)
	}

	private static runTest(String className, String methodName, JUnitPanel jUnitPanel, Closure closure) {
		try {
			jUnitPanel.running(className, methodName)
			closure()
			jUnitPanel.passed(methodName)
		} catch (Exception e) {
			jUnitPanel.error(methodName, asString(e.cause))
		} catch (AssertionError e) {
			jUnitPanel.failed(methodName, asString(e.cause))
		}
	}

	private static String asString(Throwable throwable) {
		if (throwable == null) ""
		else {
			def writer = new StringWriter()
			throwable.printStackTrace(new PrintWriter(writer))
			writer.buffer.toString()
		}
	}
}
