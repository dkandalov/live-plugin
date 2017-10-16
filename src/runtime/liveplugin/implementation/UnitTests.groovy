package liveplugin.implementation

import com.intellij.execution.testframework.TestsUIUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.NotificationsAdapter
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.annotations.NotNull

import static liveplugin.implementation.Misc.newDisposable
import static liveplugin.implementation.Misc.registerDisposable
import static liveplugin.implementation.Misc.unregisterDisposable

class UnitTests {
	private final MessageBusConnection busConnection
	private final Listener listener

	static registerUnitTestListener(Disposable disposable, Listener listener) {
		Projects.registerProjectListener(disposable) { Project project ->
			registerUnitTestListener(disposable, project, listener)
		}
	}

	static registerUnitTestListener(Disposable disposable, Project project, Listener listener) {
		def unitTests = new UnitTests(project, listener)
		unitTests.start()
		newDisposable([project, disposable]) {
			unitTests.stop()
		}
	}

	static registerUnitTestListener(String id, Project project, Listener listener) {
		registerUnitTestListener(registerDisposable(id), project, listener)
	}

	static unregisterUnitTestListener(String id) {
		unregisterDisposable(id)
	}

	UnitTests(Project project, Listener listener) {
		this.listener = listener
		this.busConnection = project.messageBus.connect()
	}

	UnitTests start() {
		busConnection.subscribe(Notifications.TOPIC, new NotificationsAdapter() {
			@Override void notify(@NotNull Notification notification) {
				if (notification.groupId == TestsUIUtil.NOTIFICATION_GROUP.displayId) {
					boolean testsFailed = (notification.type == NotificationType.ERROR)
					if (testsFailed) {
						listener.onUnitTestFailed()
					} else {
						listener.onUnitTestSucceeded()
					}
				}
			}
		})
		this
	}

	UnitTests stop() {
		busConnection.disconnect()
		this
	}

	static class Listener {
		void onUnitTestSucceeded() {}
		void onUnitTestFailed() {}
	}
}
