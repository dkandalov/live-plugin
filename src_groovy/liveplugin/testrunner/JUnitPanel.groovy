package liveplugin.testrunner

import com.intellij.execution.ExecutionManager
import com.intellij.execution.Executor
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.junit.JUnitConfigurationType
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.testframework.JavaAwareTestConsoleProperties
import com.intellij.execution.testframework.JavaTestLocator
import com.intellij.execution.testframework.SourceScope
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.testframework.sm.runner.ui.TestResultsViewer
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.actions.CloseAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.components.panels.NonOpaquePanel
import org.jetbrains.annotations.NotNull

import javax.swing.*
import java.awt.*

import static com.intellij.rt.execution.junit.states.PoolOfTestStates.ERROR_INDEX
import static com.intellij.rt.execution.junit.states.PoolOfTestStates.FAILED_INDEX

class JUnitPanel implements TestReporter {
	private static final Icon INTEGRATION_TEST_TAB_ICON = AllIcons.Nodes.TestSourceFolder;
	private static final Icon RERUN_PLUGIN_TEST_ICON = AllIcons.Actions.Execute;

	@Delegate private TestProxyUpdater testProxyUpdater
	private ProcessHandler processHandler
	private long allTestsStartTime

	def showIn(Project project, Closure rerunCallback, RunContentDescriptor descriptorToReuse = null) {
		def executor = DefaultRunExecutor.runExecutorInstance

		def junitConfiguration = new JUnitConfiguration("Temp config", project, new JUnitConfigurationType().configurationFactories.first())
		def consoleProperties = new JUnitConsoleProperties(junitConfiguration, executor)

		processHandler = new ProcessHandler() {
			@Override protected void destroyProcessImpl() { notifyProcessTerminated(0) }
			@Override protected void detachProcessImpl() { notifyProcessDetached() }
			@Override boolean detachIsDefault() { true }
			@Override OutputStream getProcessInput() { new ByteArrayOutputStream() }
		}

		def consoleView = SMTestRunnerConnectionUtil.createAndAttachConsole(
				"Plugin integration tests", processHandler, consoleProperties
		) as SMTRunnerConsoleView

		def wrapper = new NonOpaquePanel(new BorderLayout(0, 0))
		def descriptor = new RunContentDescriptor(consoleView.console, processHandler, wrapper, "Plugin integration tests") {
			@Override Icon getIcon() { INTEGRATION_TEST_TAB_ICON }
			@Override boolean isContentReuseProhibited() { false }
		}
		def toolbar = createActionToolbar(rerunCallback, descriptor, project, wrapper)
		wrapper.add(toolbar.component, BorderLayout.WEST)
		wrapper.add(consoleView.component, BorderLayout.CENTER)

		// disposers as it's done in com.intellij.execution.junit.TestObject.execute
		Disposer.register(project, consoleView)
		Disposer.register(consoleView, descriptor)

		// the line below was picked up from com.intellij.execution.impl.ExecutionManagerImpl.startRunProfile
		ExecutionManager.getInstance(project).contentManager.showRunContent(executor, descriptor, descriptorToReuse)

		testProxyUpdater = new TestProxyUpdater(consoleView.resultsViewer, consoleView.resultsViewer)
		this
	}

	private static createActionToolbar(Closure rerunCallback, RunContentDescriptor descriptor, Project project, JComponent targetComponent) {
		def actionGroup = new DefaultActionGroup()
		actionGroup.add(new DumbAwareAction("Rerun plugin integration tests", "", RERUN_PLUGIN_TEST_ICON) {
			@Override void actionPerformed(AnActionEvent e) {
				rerunCallback(descriptor)
			}
		})
		actionGroup.add(new CloseAction(DefaultRunExecutor.runExecutorInstance, descriptor, project))

		def toolbar = ActionManager.instance.createActionToolbar(ActionPlaces.UNKNOWN, actionGroup, false)
		toolbar.setTargetComponent(targetComponent)
		toolbar
	}

	void startedAllTests(long time) {
		processHandler.startNotify()
		allTestsStartTime = time
	}

	void finishedAllTests(long time) {
		processHandler.destroyProcess()
		testProxyUpdater.finished()
	}


	@SuppressWarnings("GroovyUnusedDeclaration") // used through @Delegate annotation
	private static class TestProxyUpdater {
		private final def testProxyByClassName = new HashMap<String, SMTestProxy>().withDefault{ String className ->
			int i = className.lastIndexOf(".")
			if (i == -1 || i == className.size() - 1) {
				new SMTestProxy(className, true, null)
			} else {
				def classPackage = className[0..i - 1]
				def simpleClassName = className[i + 1..-1]
				new SMTestProxy(simpleClassName, true, classPackage)
			}
		}
		private final def testProxyByMethodName = new HashMap<String, SMTestProxy>().withDefault{ methodName ->
			new SMTestProxy(methodName, false, null)
		}
		private final def testStartTimeByMethodName = new HashMap<String, Long>()

        private final TestResultsViewer resultsViewer
		private final SMTRunnerEventsListener eventsListener
		private final SMTestProxy.SMRootTestProxy rootTestProxy
		private boolean isStarted = false

		TestProxyUpdater(TestResultsViewer resultsViewer, SMTRunnerEventsListener eventsListener) {
			this.resultsViewer = resultsViewer
			this.eventsListener = eventsListener
			this.rootTestProxy = resultsViewer.testsRootNode as SMTestProxy.SMRootTestProxy
		}

		void running(String className, String methodName, long time = System.currentTimeMillis()) {
			if (!isStarted) {
				eventsListener.onTestingStarted(rootTestProxy)
				isStarted = true
			}

			def classTestProxy = testProxyByClassName.get(className)
			if (!rootTestProxy.children.contains(classTestProxy)) {
				rootTestProxy.addChild(classTestProxy)
				eventsListener.onSuiteStarted(classTestProxy)
			}

			def methodTestProxy = testProxyByMethodName.get(methodName)
			if (!classTestProxy.children.contains(methodTestProxy)) {
				classTestProxy.addChild(methodTestProxy)
				eventsListener.onTestStarted(methodTestProxy)
				testStartTimeByMethodName[methodName] = time
			}
		}

		void passed(String methodName, long time = System.currentTimeMillis()) {
			def testProxy = testProxyByMethodName.get(methodName)
			testProxy.setFinished()
			testProxy.setDuration(time - testStartTimeByMethodName[methodName])
			eventsListener.onTestFinished(testProxy)
		}

		void failed(String methodName, String error, long time = System.currentTimeMillis()) {
			def testProxy = testProxyByMethodName.get(methodName)
			testProxy.setTestFailed("", error, false)
			testProxy.setDuration(time - testStartTimeByMethodName[methodName])
			eventsListener.onTestFailed(testProxy)
		}

		void error(String methodName, String error, long time = System.currentTimeMillis()) {
			def testProxy = testProxyByMethodName.get(methodName)
			testProxy.setTestFailed("", error, true)
			testProxy.setDuration(time - testStartTimeByMethodName[methodName])
			eventsListener.onTestFailed(testProxy)
		}

		void ignored(String methodName) {
			def testProxy = testProxyByMethodName.get(methodName)
			testProxy.setTestIgnored("", "")
			eventsListener.onTestIgnored(testProxy)
		}

		void finishedClass(String className, long time = System.currentTimeMillis()) {
			def testProxy = testProxyByClassName.get(className)
			def hasChildWith = { int state -> testProxy.children.any{ it.magnitude == state } }

			if (hasChildWith(FAILED_INDEX)) testProxy.setTestFailed("", "", false)
			else if (hasChildWith(ERROR_INDEX)) testProxy.setTestFailed("", "", true)
			else testProxy.setFinished()

			eventsListener.onSuiteFinished(testProxy)
		}

		void finished() {
			def hasChildWith = { state -> rootTestProxy.children.any{ it.magnitude == state } }

			if (hasChildWith(FAILED_INDEX)) rootTestProxy.setTestFailed("", "", false)
			else if (hasChildWith(ERROR_INDEX)) rootTestProxy.setTestFailed("", "", true)
			else rootTestProxy.setFinished()

			eventsListener.onTestingFinished(rootTestProxy)
		}
	}

	/**
	 * Copy of IJ source code to avoid dependency on deprecated test API.
	 */
	private static class JUnitConsoleProperties extends JavaAwareTestConsoleProperties<JUnitConfiguration> {
		public JUnitConsoleProperties(@NotNull JUnitConfiguration configuration, Executor executor) {
			super("JUnit", configuration, executor);
		}

		@NotNull protected GlobalSearchScope initScope() {
			JUnitConfiguration.Data persistentData = ((JUnitConfiguration)this.getConfiguration()).getPersistentData();
			String testObject = persistentData.TEST_OBJECT;
			if(!"category".equals(testObject) && !"pattern".equals(testObject) && !"package".equals(testObject)) {
				return super.initScope();
			} else {
				SourceScope sourceScope = persistentData.getScope().getSourceScope(this.getConfiguration());
				return sourceScope != null?sourceScope.getGlobalSearchScope():GlobalSearchScope.allScope(this.getProject());
			}
		}

		public void appendAdditionalActions(DefaultActionGroup actionGroup, JComponent parent, TestConsoleProperties target) {
			super.appendAdditionalActions(actionGroup, parent, target);
			actionGroup.add(this.createIncludeNonStartedInRerun(target));
		}

		public SMTestLocator getTestLocator() {
			return JavaTestLocator.INSTANCE;
		}
	}
}
