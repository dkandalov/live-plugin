package liveplugin.implementation

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.notification.NotificationsAdapter
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.vcs.impl.CheckinHandlersManager
import com.intellij.openapi.vcs.impl.CheckinHandlersManagerImpl
import com.intellij.openapi.vcs.update.UpdatedFilesListener
import com.intellij.util.messages.MessageBusConnection
import org.jetbrains.annotations.NotNull

import static liveplugin.implementation.Misc.*

class VcsActions {
	private final MessageBusConnection busConnection
	private final UpdatedFilesListener updatedListener
	private final CheckinHandlerFactory checkinListener
	private final NotificationsAdapter pushListener

	static registerVcsListener(Disposable disposable, Listener listener) {
		Projects.registerProjectListener(disposable) { Project project ->
			registerVcsListener(newDisposable([project, disposable]), project, listener)
		}
	}

	static registerVcsListener(Disposable disposable, Project project, Listener listener) {
		def vcsActions = new VcsActions(project, listener)
		newDisposable([project, disposable]) {
			vcsActions.stop()
		}
		vcsActions.start()
	}

	static registerVcsListener(String id, Project project, Listener listener) {
		registerVcsListener(registerDisposable(id), project, listener)
	}

	static unregisterVcsListener(String id) {
		unregisterDisposable(id)
	}

	VcsActions(Project project, Listener listener) {
		this.busConnection = project.messageBus.connect()

		updatedListener = new UpdatedFilesListener() {
			@Override void consume(Set<String> files) {
				listener.onVcsUpdate()
			}
		}

		checkinListener = new CheckinHandlerFactory() {
			@NotNull @Override CheckinHandler createHandler(@NotNull CheckinProjectPanel panel, @NotNull CommitContext commitContext) {
				new CheckinHandler() {
					@Override void checkinSuccessful() {
						if (panel.project == project) {
							listener.onVcsCommit()
						}
					}
					@Override void checkinFailed(List<VcsException> exception) {
						if (panel.project == project) {
							listener.onVcsCommitFailed()
						}
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
		checkinHandlers().add(0, checkinListener)
		this
	}

	VcsActions stop() {
		busConnection.disconnect()
		checkinHandlers().remove(checkinListener)
		this
	}

	private static List checkinHandlers() {
		def checkinHandlersManager = CheckinHandlersManager.instance as CheckinHandlersManagerImpl
		accessField(checkinHandlersManager, ["myRegisteredBeforeCheckinHandlers", "a", "b"], List)
	}

	private static boolean isVcsNotification(Notification notification) {
		notification.groupId == "Vcs Messages" ||
		notification.groupId == "Vcs Important Messages" ||
		notification.groupId == "Vcs Minor Notifications" ||
		notification.groupId == "Vcs Silent Notifications"
	}

	private static boolean matchTitleOf(Notification notification, String... expectedTitles) {
		for (String title : expectedTitles) {
			if (notification.getTitle().startsWith(title)) return true
		}
		false
	}

	/**
	 * Listener callbacks can be called from any thread.
	 */
	static class Listener {
		void onVcsCommit() {}
		void onVcsCommitFailed() {}
		void onVcsUpdate() {}
		void onVcsPush() {}
		void onVcsPushFailed() {}
	}
}