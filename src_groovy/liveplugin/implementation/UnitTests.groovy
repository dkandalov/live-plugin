package liveplugin.implementation

import com.intellij.execution.testframework.TestsUIUtil
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.NotificationsAdapter
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.annotations.NotNull

class UnitTests {
	private final MessageBusConnection busConnection
	private final Listener listener

	static registerUnitTestListener(String id, Project project, Listener listener) {
		GlobalVars.changeGlobalVar(id) { oldUnitTests ->
			if (oldUnitTests != null) oldUnitTests.stop()
			new UnitTests(project, listener).start()
		}
	}

	static unregisterUnitTestListener(String id) {
		def oldUnitTests = GlobalVars.removeGlobalVar(id)
		if (oldUnitTests != null) oldUnitTests.stop()
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
