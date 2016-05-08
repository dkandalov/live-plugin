package liveplugin.testrunner

import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.project.Project
import liveplugin.PluginUtil
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.junit.Ignore
import org.junit.Test

import java.lang.reflect.Method


class IntegrationTestsRunner implements ApplicationComponent {
    // by default use text runner because it will work in all IntelliJ IDEs
	private static runTests = IntegrationTestsTextRunner.&runIntegrationTests

	/**
	 * Runs tests in specified {@code testClasses} reporting results using
	 * JUnit panel (for IDEs with JUnit and Java support) or in text console (for other IDEs).
	 * All tests are executed on the same non-EDT thread.
	 *
	 * @param testClasses classes with tests. Note that this is not standard JUnit runner and it only supports
	 *                    {@link Test} and {@link Ignore} annotations.
	 *                    (New class instance is created for each test method.)
	 * @param project current project
	 * @param pluginPath (optional)
	 * @return
	 */
	static void runIntegrationTests(List<Class> testClasses, @NotNull Project project, @Nullable String pluginPath = null) {
		runTests(testClasses, project, pluginPath)
	}

	@Override void initComponent() {
        // only initialized if there is junit plugin (which might not be the case for some IntelliJ IDEs)
		runTests = IntegrationTestsUIRunner.&runIntegrationTests
		PluginUtil.log("Enabled junit panel output for integration tests")
	}

	@Override void disposeComponent() {}

	@Override String getComponentName() { this.class.simpleName }


	static runTestsInClass(Class testClass, Map context, TestReporter testReport, Closure<Long> now) {
		def isTest = { Method method -> method.annotations.find{ it instanceof Test} }
		def isIgnored = { Method method -> method.annotations.find{ it instanceof Ignore} }

		testClass.declaredMethods.findAll{ isTest(it) }.each{ method ->
			if (isIgnored(method)) {
				ignoreTest(testClass.name, method.name, testReport, now())
			} else {
				runTest(testClass.name, method.name, testReport, now) {
					method.invoke(createInstanceOf(testClass, context))
				}
			}
		}

		testReport.finishedClass(testClass.name, now())
	}

	private static ignoreTest(String className, String methodName, TestReporter testReport, long now) {
		testReport.running(className, methodName, now)
		testReport.ignored(methodName)
	}

	private static runTest(String className, String methodName, TestReporter testReport, Closure<Long> now, Closure closure) {
		try {
			testReport.running(className, methodName, now())
			closure()
			testReport.passed(methodName, now())
		} catch (AssertionError e) {
			testReport.failed(methodName, asString(e.cause), now())
		} catch (Throwable t) {
			if (t.cause instanceof AssertionError) {
				testReport.failed(methodName, asString(t.cause), now())
			} else {
				testReport.error(methodName, asString(t.cause), now())
			}
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

	private static Object createInstanceOf(Class testClass, Map context) {
		def hasConstructorWithContext = testClass.constructors.any{
			it.parameterTypes.size() == 1 && it.parameterTypes.first() == Map
		}
		if (hasConstructorWithContext) return testClass.newInstance(context)

		def hasDefaultConstructor = testClass.constructors.any{ it.parameterTypes.size() == 0 }
		if (hasDefaultConstructor) return testClass.newInstance()

		throw new IllegalStateException("Failed to create test class ${testClass}")
	}

}
