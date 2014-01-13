package liveplugin.testrunner

interface TestReport {
	def startedAllTests(long time)
	def finishedAllTests(long time)

	def running(String className, String methodName, long time)
	def passed(String methodName, long time)
	def failed(String methodName, String error, long time)
	def error(String methodName, String error, long time)
	def ignored(String methodName)
	def finishedClass(String className, long time)
}
