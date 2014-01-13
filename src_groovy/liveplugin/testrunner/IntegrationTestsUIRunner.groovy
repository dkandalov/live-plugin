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
	static void runIntegrationTests(List<Class> testClasses, @NotNull Project project, @Nullable String pluginPath = null) {
		def jUnitPanel = new JUnitPanel().showIn(project)

		jUnitPanel.startedAllTests()
		def context = [project: project, pluginPath: pluginPath]
		def now = System.currentTimeMillis()
		testClasses.collect{ runTestsInClass(it, context, jUnitPanel, now) }
		jUnitPanel.finishedAllTests()
	}

	private static runTestsInClass(Class testClass, Map context, TestReport testReport, long now) {
		def isTest = { Method method -> method.annotations.find{ it instanceof Test} }
		def isIgnored = { Method method -> method.annotations.find{ it instanceof Ignore} }

		testClass.declaredMethods.findAll{ isTest(it) }.each{ method ->
			if (isIgnored(method)) {
				ignoreTest(testClass.name, method.name, testReport, now)
			} else {
				runTest(testClass.name, method.name, testReport, now) {
					method.invoke(createInstanceOf(testClass, context))
				}
			}
		}

		testReport.finishedClass(testClass.name, now)
	}

	private static ignoreTest(String className, String methodName, TestReport testReport, long now) {
		testReport.running(className, methodName, now)
		testReport.ignored(methodName)
	}

	private static runTest(String className, String methodName, TestReport testReport, long now, Closure closure) {
		try {
			testReport.running(className, methodName, now)
			closure()
			testReport.passed(methodName, now)
		} catch (Exception e) {
			testReport.error(methodName, asString(e.cause), now)
		} catch (AssertionError e) {
			testReport.failed(methodName, asString(e.cause), now)
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
