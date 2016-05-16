package liveplugin.implementation

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task

import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function

import static com.intellij.openapi.progress.PerformInBackgroundOption.ALWAYS_BACKGROUND

class Threads {
	static <T> T invokeOnEDT(Function<Void, T> function) {
		def result = null
		ApplicationManager.application.invokeAndWait(new Runnable() {
			@Override void run() {
				//noinspection GrReassignedInClosureLocalVar
				result = function.apply(null)
			}
		}, ModalityState.any())
		(T) result
	}

	static void invokeLaterOnEDT(Function<Void, Object> function) {
		ApplicationManager.application.invokeLater(new Runnable() {
			@Override void run() {
				function.apply(null)
			}
		})
	}

	static <T> void doInBackground(String taskDescription = "A task", boolean canBeCancelledByUser = true,
	                      PerformInBackgroundOption backgroundOption = ALWAYS_BACKGROUND,
	                      Function<ProgressIndicator, T> task,
                          Function<Void, Void> whenCancelled = {},
                          Function<T, Void> whenDone = {}) {
		invokeOnEDT {
			AtomicReference<T> result = new AtomicReference<>(null)
			new Task.Backgroundable(null, taskDescription, canBeCancelledByUser, backgroundOption) {
				@Override void run(ProgressIndicator indicator) { result.set(task.apply(indicator)) }
				@Override void onSuccess() { whenDone.apply(result.get()) }
				@Override void onCancel() { whenCancelled.apply(null) }
			}.queue()
		}
	}

	static doInModalMode(String taskDescription = "A task", boolean canBeCancelledByUser = true,
	                     Function<ProgressIndicator, Void> task) {
		new Task.Modal(null, taskDescription, canBeCancelledByUser) {
			@Override void run(ProgressIndicator indicator) { task.apply(indicator) }
		}.queue()
	}
}
