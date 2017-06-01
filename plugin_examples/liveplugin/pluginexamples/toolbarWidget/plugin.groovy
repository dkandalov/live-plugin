import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.util.Alarm
import com.intellij.util.Consumer

import java.awt.*
import java.awt.event.MouseEvent

import static liveplugin.PluginUtil.*
import static liveplugin.implementation.Misc.scheduleTask

if (isIdeStartup) return

def fibonacci(long n1 = 0, long n2 = 1) {
	[n1 + n2, {-> fibonacci(n2, n1 + n2)}]
}

invokeOnEDT {
	def (n, nextFibonacci) = fibonacci()

	def presentation = new StatusBarWidget.TextPresentation() {
		@Override String getText() { "Fibonacci: ${n}" }
		@Override float getAlignment() { Component.CENTER_ALIGNMENT }
		@Override String getTooltipText() { "Click to reset Fibonacci counter" }
		@Override Consumer<MouseEvent> getClickConsumer() {
			return new Consumer<MouseEvent>() {
				@Override void consume(MouseEvent mouseEvent) {
					(n, nextFibonacci) = fibonacci()
					updateWidget("FibonacciWidget")
				}
			}
		}
		@Deprecated @Override String getMaxPossibleText() { "" }
	}
	def widgetId = "FibonacciWidget"
	registerWidget(widgetId, pluginDisposable, presentation)

	scheduleTask(new Alarm(Alarm.ThreadToUse.SWING_THREAD, pluginDisposable), 2000) {
		(n, nextFibonacci) = nextFibonacci()
		if (n > 2_000_000) {
			unregisterWidget(widgetId)
		} else {
			updateWidget(widgetId)
		}
	}
}
