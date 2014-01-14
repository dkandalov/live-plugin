package liveplugin.testrunner

interface TestReport {
	void startedAllTests(long time)
	void finishedAllTests(long time)
	void running(String className, String methodName, long time)
	void passed(String methodName, long time)
	void failed(String methodName, String error, long time)
	void error(String methodName, String error, long time)
	void ignored(String methodName)
	void finishedClass(String className, long time)
}
