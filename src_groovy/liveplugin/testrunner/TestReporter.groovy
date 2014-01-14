package liveplugin.testrunner

import com.intellij.openapi.application.ApplicationManager

interface TestReporter {
	void startedAllTests(long time)
	void finishedAllTests(long time)
	void running(String className, String methodName, long time)
	void passed(String methodName, long time)
	void failed(String methodName, String error, long time)
	void error(String methodName, String error, long time)
	void ignored(String methodName)
	void finishedClass(String className, long time)
}

class TestReporterOnEdt implements TestReporter {
	private final TestReporter testReport

	TestReporterOnEdt(TestReporter testReport) {
		this.testReport = testReport
	}

	@Override void startedAllTests(long time) {
		onEdt{ testReport.startedAllTests(time) }
	}

	@Override void finishedAllTests(long time) {
		onEdt{ testReport.finishedAllTests(time) }
	}

	@Override void running(String className, String methodName, long time) {
		onEdt{ testReport.running(className, methodName, time) }
	}

	@Override void passed(String methodName, long time) {
		onEdt{ testReport.passed(methodName, time) }
	}

	@Override void failed(String methodName, String error, long time) {
		onEdt{ testReport.failed(methodName, error, time) }
	}

	@Override void error(String methodName, String error, long time) {
		onEdt{ testReport.error(methodName, error, time) }
	}

	@Override void ignored(String methodName) {
		onEdt{ testReport.ignored(methodName) }
	}

	@Override void finishedClass(String className, long time) {
		onEdt{ testReport.finishedClass(className, time) }
	}

	private static def onEdt(Closure closure) {
		ApplicationManager.application.invokeLater(closure)
	}
}