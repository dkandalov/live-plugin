@file:Suppress("unused")

package liveplugin

import com.intellij.openapi.util.Key
import liveplugin.implementation.pluginrunner.kotlin.LivePluginScript
import java.util.function.Function

fun LivePluginScript.showBreakpointStatusBar(lineNumber: Int) {
    val liveStatusManagerFunctions = Key.findKeyByName("SPP_LIVE_STATUS_MANAGER_FUNCTIONS")!!
    val consumer = project.getUserData(liveStatusManagerFunctions) as Function<Array<Any?>, Any?>
    consumer.apply(arrayOf("showBreakpointStatusBar", project.currentEditor!!, lineNumber))
}

fun LivePluginScript.showLogStatusBar(lineNumber: Int) {
    val liveStatusManagerFunctions = Key.findKeyByName("SPP_LIVE_STATUS_MANAGER_FUNCTIONS")!!
    val consumer = project.getUserData(liveStatusManagerFunctions) as Function<Array<Any?>, Any?>
    consumer.apply(arrayOf("showLogStatusBar", project.currentEditor!!, lineNumber))
}

fun LivePluginScript.showMeterStatusBar(lineNumber: Int) {
    val liveStatusManagerFunctions = Key.findKeyByName("SPP_LIVE_STATUS_MANAGER_FUNCTIONS")!!
    val consumer = project.getUserData(liveStatusManagerFunctions) as Function<Array<Any?>, Any?>
    consumer.apply(arrayOf("showMeterStatusBar", project.currentEditor!!, lineNumber))
}

fun LivePluginScript.showSpanStatusBar(lineNumber: Int) {
    val liveStatusManagerFunctions = Key.findKeyByName("SPP_LIVE_STATUS_MANAGER_FUNCTIONS")!!
    val consumer = project.getUserData(liveStatusManagerFunctions) as Function<Array<Any?>, Any?>
    consumer.apply(arrayOf("showSpanStatusBar", project.currentEditor!!, lineNumber))
}
