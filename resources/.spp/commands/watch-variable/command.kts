import com.intellij.notification.NotificationType.ERROR
import com.intellij.openapi.application.runReadAction
import io.vertx.core.json.Json
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.coroutines.await
import spp.command.LiveCommand
import spp.command.LiveCommandContext
import spp.jetbrains.marker.SourceMarker
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode
import spp.jetbrains.marker.source.mark.inlay.config.InlayMarkVirtualText
import spp.jetbrains.sourcemarker.PluginUI.getCommandTypeColor
import spp.jetbrains.sourcemarker.SourceMarkerPlugin.vertx
import spp.plugin.registerCommand
import spp.plugin.show
import spp.protocol.SourceServices.Provide.toLiveInstrumentSubscriberAddress
import spp.protocol.artifact.ArtifactNameUtils
import spp.protocol.instrument.LiveBreakpoint
import spp.protocol.instrument.LiveSourceLocation
import spp.protocol.instrument.event.LiveInstrumentEvent
import spp.protocol.instrument.event.LiveInstrumentEventType.BREAKPOINT_HIT
import spp.protocol.instrument.throttle.InstrumentThrottle
import spp.protocol.instrument.throttle.ThrottleStep.SECOND
import spp.protocol.marshall.ProtocolMarshaller.deserializeLiveBreakpointHit
import java.awt.Color

class WatchVariableCommand : LiveCommand() {
    override val name = "watch-variable"
    override val description = "<html><span style=\"font-size: 90%; color: ${getCommandTypeColor()}\">" +
            "Adds live breakpoint to display the variable's current value" + "</span></html>"
    override val selectedIcon: String = "watch-variable/icons/watch-variable_selected.svg"
    override val unselectedIcon: String = "watch-variable/icons/watch-variable_unselected.svg"

    override suspend fun triggerSuspend(context: LiveCommandContext) {
        val instrumentService = liveInstrumentService
        if (instrumentService == null) {
            show("Unable to find active services", notificationType = ERROR)
            return
        }
        val variableName = context.variableName
        if (variableName == null) {
            show("Unable to determine variable name", notificationType = ERROR)
            return
        }
        val selfId = liveService.getSelf().await().developer.id

        instrumentService.addLiveInstrument(LiveBreakpoint(
                LiveSourceLocation(
                        ArtifactNameUtils.getQualifiedClassName(context.artifactQualifiedName.identifier)!!,
                        context.lineNumber + 1
                ),
                throttle = InstrumentThrottle(1, SECOND),
                hitLimit = -1
        )).onComplete {
            if (it.succeeded()) {
                val instrumentId = it.result().id!!
                runReadAction {
                    addInlay(context, selfId, instrumentId, variableName)
                }
            } else {
                show(it.cause().message, notificationType = ERROR)
            }
        }
    }

    private fun addInlay(context: LiveCommandContext, selfId: String, instrumentId: String, variableName: String?) {
        val inlay = SourceMarker.creationService.createExpressionInlayMark(
                context.fileMarker, context.lineNumber, false
        )
        val virtualText = InlayMarkVirtualText(inlay, " // Live value: n/a")
        virtualText.useInlinePresentation = true
        virtualText.textAttributes.foregroundColor = Color.orange
        inlay.configuration.activateOnMouseClick = false
        inlay.configuration.virtualText = virtualText
        inlay.apply(true)

        val consumer = vertx.eventBus().consumer<JsonObject>(toLiveInstrumentSubscriberAddress(selfId))
        inlay.addEventListener {
            if (it.eventCode == SourceMarkEventCode.MARK_REMOVED) {
                show("Removed live instrument for watched variable: $variableName")
                liveInstrumentService!!.removeLiveInstrument(instrumentId)
                consumer.unregister()
            }
        }

        consumer.handler {
            val liveEvent = Json.decodeValue(it.body().toString(), LiveInstrumentEvent::class.java)
            if (liveEvent.eventType == BREAKPOINT_HIT) {
                val bpHit = deserializeLiveBreakpointHit(JsonObject(liveEvent.data))
                if (bpHit.breakpointId == instrumentId) {
                    val liveVariables = bpHit.stackTrace.first().variables
                    val liveVar = liveVariables.find { it.name == variableName }
                    if (liveVar != null) {
                        virtualText.updateVirtualText(" // Live value: " + liveVar.value)
                    }
                }
            }
        }
    }
}

registerCommand(WatchVariableCommand())
