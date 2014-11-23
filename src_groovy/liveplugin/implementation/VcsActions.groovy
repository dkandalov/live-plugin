package liveplugin.implementation

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.notification.NotificationsAdapter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.vcs.impl.CheckinHandlersManager
import com.intellij.openapi.vcs.update.UpdatedFilesListener
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.annotations.NotNull

class VcsActions {
	private final MessageBusConnection busConnection
	private final UpdatedFilesListener updatedListener
	private final CheckinHandlerFactory checkinListener
	private final NotificationsAdapter pushListener

	static addVcsListener(String id, Project project, Listener listener) {
		GlobalVars.changeGlobalVar(id){ oldVcsActions ->
			if (oldVcsActions != null) oldVcsActions.stop()
			new VcsActions(project, listener).start()
		}
	}

	static removeVcsListener(String id) {
		def oldVcsActions = GlobalVars.removeGlobalVar(id)
		if (oldVcsActions != null) oldVcsActions.stop()
	}

	VcsActions(Project project, Listener listener) {
		this.busConnection = project.getMessageBus().connect()

		updatedListener = new UpdatedFilesListener() {
			@Override void consume(Set<String> files) {
				listener.onVcsUpdate()
			}
		}
		checkinListener = new CheckinHandlerFactory() {
			@NotNull @Override CheckinHandler createHandler(@NotNull CheckinProjectPanel panel, @NotNull CommitContext commitContext) {
				new CheckinHandler() {
					@Override void checkinSuccessful() {
						listener.onVcsCommit()
					}
				}
			}
		}

		// see git4idea.push.GitPushResultNotification#create
		// see org.zmlx.hg4idea.push.HgPusher#push
		pushListener = new NotificationsAdapter() {
			@Override void notify(@NotNull Notification notification) {
				if (!isVcsNotification(notification)) return

				if (matchTitleOf(notification, "Push successful")) {
					listener.onVcsPush()
				} else if (matchTitleOf(notification, "Push failed", "Push partially failed", "Push rejected", "Push partially rejected")) {
					listener.onVcsPushFailed()
				}
			}
		}
	}

	VcsActions start() {
		// using bus to listen to vcs updates because normal listener calls it twice
		// (see also https://gist.github.com/dkandalov/8840509)
		busConnection.subscribe(UpdatedFilesListener.UPDATED_FILES, updatedListener)
		busConnection.subscribe(Notifications.TOPIC, pushListener)
		CheckinHandlersManager.instance.registerCheckinHandlerFactory(checkinListener)
		this
	}

	VcsActions stop() {
		busConnection.disconnect()
		CheckinHandlersManager.instance.unregisterCheckinHandlerFactory(checkinListener)
		this
	}

	private static boolean isVcsNotification(Notification notification) {
		notification.groupId.equals("Vcs Messages") ||
			notification.groupId.equals("Vcs Important Messages") ||
			notification.groupId.equals("Vcs Minor Notifications") ||
			notification.groupId.equals("Vcs Silent Notifications")
	}

	private static boolean matchTitleOf(Notification notification, String... expectedTitles) {
		for (String title : expectedTitles) {
			if (notification.getTitle().startsWith(title)) return true
		}
		false
	}

	static class Listener {
		void onVcsCommit() {}
		void onVcsUpdate() {}
		void onVcsPush() {}
		void onVcsPushFailed() {}
	}
}