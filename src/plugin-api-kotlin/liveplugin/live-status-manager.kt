package liveplugin

import com.intellij.openapi.util.Key
import spp.jetbrains.marker.extend.LiveCommand
import java.util.function.Function

fun LiveCommand.showBreakpointStatusBar(lineNumber: Int) {
    val liveStatusManagerFunctions = Key.findKeyByName("SPP_LIVE_STATUS_MANAGER_FUNCTIONS")!!
    val consumer = project.getUserData(liveStatusManagerFunctions) as Function<Array<Any?>, Any?>
    consumer.apply(arrayOf("showBreakpointStatusBar", project.currentEditor!!, lineNumber))
}

fun LiveCommand.showLogStatusBar(lineNumber: Int) {
    val liveStatusManagerFunctions = Key.findKeyByName("SPP_LIVE_STATUS_MANAGER_FUNCTIONS")!!
    val consumer = project.getUserData(liveStatusManagerFunctions) as Function<Array<Any?>, Any?>
    consumer.apply(arrayOf("showLogStatusBar", project.currentEditor!!, lineNumber))
}

fun LiveCommand.showMeterStatusBar(lineNumber: Int) {
    val liveStatusManagerFunctions = Key.findKeyByName("SPP_LIVE_STATUS_MANAGER_FUNCTIONS")!!
    val consumer = project.getUserData(liveStatusManagerFunctions) as Function<Array<Any?>, Any?>
    consumer.apply(arrayOf("showMeterStatusBar", project.currentEditor!!, lineNumber))
}

fun LiveCommand.showSpanStatusBar(lineNumber: Int) {
    val liveStatusManagerFunctions = Key.findKeyByName("SPP_LIVE_STATUS_MANAGER_FUNCTIONS")!!
    val consumer = project.getUserData(liveStatusManagerFunctions) as Function<Array<Any?>, Any?>
    consumer.apply(arrayOf("showSpanStatusBar", project.currentEditor!!, lineNumber))
}
