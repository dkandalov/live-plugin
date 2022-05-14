import spp.plugin.*
import spp.command.*
import spp.jetbrains.marker.jvm.psi.EndpointDetector
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.PORTAL_OPENING
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.UPDATE_PORTAL_CONFIG
import spp.jetbrains.sourcemarker.PluginUI.*
import spp.jetbrains.sourcemarker.PluginBundle.message

/**
 * Opens the 'Endpoint-Logs' dashboard via portal popup.
 */
class ViewLogsCommand : LiveCommand() {
    override val name = message("view_logs")
    override val description = "<html><span style=\"font-size: 80%; color: ${getCommandTypeColor()}\">" +
            message("live_view") + " ➛ " + message("logs") + " ➛ " + message("scope") +
            ": </span><span style=\"font-size: 80%; color: ${getCommandHighlightColor()}\">" + message("method") +
            "</span></html>"
    override val selectedIcon = "view-logs/icons/view-logs_selected.svg"
    override val unselectedIcon = "view-logs/icons/view-logs_unselected.svg"

    override fun trigger(context: LiveCommandContext) {
        val endpointId = context.guideMark?.getUserData(EndpointDetector.ENDPOINT_ID) ?: return
        val serviceId = endpointId.substringBefore("_")
        val pageType = "Logs"
        val newPage = "/dashboard/GENERAL/Endpoint/$serviceId/$endpointId/Endpoint-$pageType?portal=true&fullview=true"

        context.guideMark!!.triggerEvent(UPDATE_PORTAL_CONFIG, listOf("setPage", newPage)) {
            context.guideMark!!.triggerEvent(PORTAL_OPENING, listOf(PORTAL_OPENING))
        }
    }
}

registerCommand(ViewLogsCommand())
