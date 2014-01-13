package liveplugin.testrunner

interface TestReport {
	def startedAllTests(long now)
	def finishedAllTests(long now)

	def running(String className, String methodName, long now)
	def passed(String methodName, long now)
	def failed(String methodName, String error, long now)
	def error(String methodName, String error, long now)
	def ignored(String methodName)
	def finishedClass(String className, long now)
}
