package liveplugin.testrunner

import com.intellij.execution.ExecutionManager
import com.intellij.execution.Location
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.junit.JUnitConfiguration
import com.intellij.execution.junit.JUnitConfigurationType
import com.intellij.execution.junit2.TestProxy
import com.intellij.execution.junit2.info.TestInfo
import com.intellij.execution.junit2.segments.ObjectReader
import com.intellij.execution.junit2.states.Statistics
import com.intellij.execution.junit2.states.TestState
import com.intellij.execution.junit2.ui.ConsolePanel
import com.intellij.execution.junit2.ui.model.CompletionEvent
import com.intellij.execution.junit2.ui.model.JUnitRunningModel
import com.intellij.execution.junit2.ui.model.TreeCollapser
import com.intellij.execution.junit2.ui.properties.JUnitConsoleProperties
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.BasicProgramRunner
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.Printer
import com.intellij.execution.testframework.sm.SMTestRunnerConnectionUtil
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.ui.SMTRunnerConsoleView
import com.intellij.execution.testframework.sm.runner.ui.TestResultsViewer
import com.intellij.execution.testframework.ui.BaseTestsOutputConsoleView
import com.intellij.execution.testframework.ui.TestResultsPanel
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
import liveplugin.PluginUtil
import org.jetbrains.annotations.NotNull

import javax.swing.*
import java.awt.*

import static com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import static com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT
import static com.intellij.rt.execution.junit.states.PoolOfTestStates.*

class JUnitPanel implements TestReporter {
	private static final Icon INTEGRATION_TEST_TAB_ICON = AllIcons.Nodes.TestSourceFolder;
	private static final Icon RERUN_PLUGIN_TEST_ICON = AllIcons.Actions.Execute;

	@Delegate private TestProxyUpdater testProxyUpdater
	private ProcessHandler processHandler
	private JUnitRunningModel model
	private long allTestsStartTime

	def showIn(Project project, Closure rerunCallback, RunContentDescriptor descriptorToReuse = null) {
		def executor = DefaultRunExecutor.runExecutorInstance

		def junitConfiguration = new JUnitConfiguration("Temp config", project, new JUnitConfigurationType().configurationFactories.first())
		def consoleProperties = new JUnitConsoleProperties(junitConfiguration, executor)

		def configurationFactory = new JUnitConfigurationType().configurationFactories.first()
		def runnerAndConfigSettings = RunManager.getInstance(project).createRunConfiguration("Temp run config", configurationFactory)
		def environment = new ExecutionEnvironment(executor, new BasicProgramRunner(), runnerAndConfigSettings, project)

		processHandler = new ProcessHandler() {
			@Override protected void destroyProcessImpl() { notifyProcessTerminated(0) }
			@Override protected void detachProcessImpl() { notifyProcessDetached() }
			@Override boolean detachIsDefault() { true }
			@Override OutputStream getProcessInput() { new ByteArrayOutputStream() }
		}

		def rootTestProxy = new TestProxy(newTestInfo("Integration tests"))
		model = new JUnitRunningModel(rootTestProxy, consoleProperties)
        model.notifier.onFinished() // disable listening for events (see also JUnitListenersNotifier.onEvent)

		def consoleView = createConsoleView(consoleProperties, processHandler)
		consoleView.initUI()

		rootTestProxy.setPrinter(consoleView.printer)

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
		Disposer.register(consoleView, rootTestProxy)
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

	private static createConsoleView(JUnitConsoleProperties consoleProperties, ProcessHandler processHandler) {
		def consoleView = SMTestRunnerConnectionUtil.createAndAttachConsole(
				"Plugin integration tests", processHandler, consoleProperties
		) as SMTRunnerConsoleView

//		consoleView.resultsViewer.treeView.attachToModel(model)
//		model.attachToTree(consoleView.resultsViewer.treeView)
//		model.onUIBuilt()

//		def consoleView = new MyTreeConsoleView(
//				consoleProperties, environment, rootTestProxy,
//				"Plugin integration tests", processHandler
//		)
//		consoleView.initUI()
//		consoleView.attachToProcess(processHandler)

//		if (consolePanel != null) {
//			consolePanel.treeView.attachToModel(model)
//			model.attachToTree(consolePanel.treeView)
//			consolePanel.setModel(model)
//			model.onUIBuilt()
//			new TreeCollapser().setModel(model)
//		}

		consoleView
	}

	void startedAllTests(long time) {
		processHandler.startNotify()
		allTestsStartTime = time
	}

	void finishedAllTests(long time) {
		processHandler.destroyProcess()
		testProxyUpdater.finished()
		model.notifier.fireRunnerStateChanged(new CompletionEvent(true, time - allTestsStartTime))
	}

	private static TestInfo newTestInfo(String name, String comment = "") {
		new TestInfo() {
			@Override String getName() { name }
			@Override String getComment() { comment }
			@Override void readFrom(ObjectReader objectReader) {}
			@Override Location getLocation(Project project, GlobalSearchScope globalSearchScope) { null }
		}
	}


	@SuppressWarnings("GroovyUnusedDeclaration") // used through @Delegate annotation
	private static class TestProxyUpdater {
		private static final runningState = newTestState(RUNNING_INDEX, null, false)
		private static final passedState = newTestState(PASSED_INDEX)
		private static final failedState = newTestState(FAILED_INDEX)
		private static final errorState = newTestState(ERROR_INDEX)
		private static final ignoredState = newTestState(IGNORED_INDEX)

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
		private final def testStartTimeByClassName = new HashMap<String, Long>()

        private final TestResultsViewer resultsViewer
		private final SMTRunnerEventsListener eventsListener
		private boolean isStarted = false

		TestProxyUpdater(TestResultsViewer resultsViewer, SMTRunnerEventsListener eventsListener) {
			this.resultsViewer = resultsViewer
			this.eventsListener = eventsListener
		}

		void running(String className, String methodName, long time = System.currentTimeMillis()) {
			PluginUtil.show("running ${className} ${methodName}")
			try {
				if (!isStarted) {
					eventsListener.onTestingStarted(resultsViewer.testsRootNode as SMTestProxy.SMRootTestProxy)
					isStarted = true
				}

				def classTestProxy = testProxyByClassName.get(className)
				if (!resultsViewer.testsRootNode.children.contains(classTestProxy)) {
					eventsListener.onSuiteStarted(classTestProxy)
				}

				def methodTestProxy = testProxyByMethodName.get(methodName)
				if (!classTestProxy.children.contains(methodTestProxy)) {
					eventsListener.onTestStarted(methodTestProxy)
					classTestProxy.addChild(methodTestProxy)
				}
			} catch (Exception e) {
				PluginUtil.show(e)
			}
		}

		void passed(String methodName, long time = System.currentTimeMillis()) {
			PluginUtil.show("passed ${methodName}")
			eventsListener.onTestFinished(testProxyByMethodName.get(methodName))
		}

		void failed(String methodName, String error, long time = System.currentTimeMillis()) {
			PluginUtil.show("failed ${methodName}")
			eventsListener.onTestFailed(testProxyByMethodName.get(methodName))
		}

		void error(String methodName, String error, long time = System.currentTimeMillis()) {
			PluginUtil.show("error ${methodName}")
			eventsListener.onTestFailed(testProxyByMethodName.get(methodName)) // TODO same code as above
		}

		void ignored(String methodName) {
			PluginUtil.show("ignored ${methodName}")
			eventsListener.onTestIgnored(testProxyByMethodName.get(methodName))
		}

		void finishedClass(String className, long time = System.currentTimeMillis()) {
			PluginUtil.show("finishedClass ${className}")
			def testProxy = testProxyByClassName.get(className)
			eventsListener.onSuiteFinished(testProxy)

//			def hasChildWith = { int state -> testProxy.children.any{ it.state.magnitude == state } }
//
//			if (hasChildWith(FAILED_INDEX)) testProxy.state = failedState
//			else if (hasChildWith(ERROR_INDEX)) testProxy.state = errorState
//			else testProxy.state = passedState
//
//			testProxy.statistics = statisticsWithDuration((int) time - testStartTimeByClassName.get(className))
		}

		void finished() {
			eventsListener.onTestingFinished(resultsViewer.testsRootNode as SMTestProxy.SMRootTestProxy)
//			def hasChildWith = { state -> rootTestProxy.children.any{ it.state.magnitude == state } }
//
//			if (hasChildWith(FAILED_INDEX)) rootTestProxy.state = failedState
//			else if (hasChildWith(ERROR_INDEX)) rootTestProxy.state = errorState
//			else rootTestProxy.state = passedState
		}

		private static Statistics statisticsWithDuration(int testMethodDuration) {
			new Statistics() {
				@Override int getTime() { testMethodDuration }
			}
		}

		private static TestState newTestState(int state, String message = null, boolean isFinal = true) {
			new TestState() {
				@Override int getMagnitude() { state }
				@Override boolean isFinal() { isFinal }
				@Override void printOn(Printer printer) {
					if (message != null) {
						def contentType = (state == FAILED_INDEX || state == ERROR_INDEX) ? ERROR_OUTPUT : NORMAL_OUTPUT
						printer.print(message, contentType)
					}
				}
			}
		}
	}


	/**
	 * Originally a copy of com.intellij.execution.junit2.ui.JUnitTreeConsoleView in attempt to "reconfigure" ConsolePanel
	 */
	private static class MyTreeConsoleView extends BaseTestsOutputConsoleView {
		private final JUnitConsoleProperties properties
		private final ExecutionEnvironment environment
		private final String consoleTitle
		private final ProcessHandler processHandler

		private ConsolePanel consolePanel


		MyTreeConsoleView(JUnitConsoleProperties properties, ExecutionEnvironment environment,
		                  AbstractTestProxy unboundOutputRoot, String consoleTitle, ProcessHandler processHandler) {
			super(properties, unboundOutputRoot)
			this.properties = properties
			this.environment = environment
			this.consoleTitle = consoleTitle
			this.processHandler = processHandler
		}

		@Override protected TestResultsPanel createTestResultsPanel() {
			consolePanel = new ConsolePanel(console.component, printer, properties, console.createConsoleActions())
			consolePanel
		}

		@Override void attachToProcess(final ProcessHandler processHandler) {
			super.attachToProcess(processHandler)
			consolePanel.onProcessStarted(processHandler)
		}

		@Override void dispose() {
			super.dispose()
			consolePanel = null
		}

		@Override JComponent getPreferredFocusableComponent() {
			consolePanel.treeView
		}

		void attachToModel(@NotNull JUnitRunningModel model) {
			if (consolePanel != null) {
				consolePanel.treeView.attachToModel(model)
				model.attachToTree(consolePanel.treeView)
				consolePanel.setModel(model)
				model.onUIBuilt()
				new TreeCollapser().setModel(model)
			}
		}
	}
}
