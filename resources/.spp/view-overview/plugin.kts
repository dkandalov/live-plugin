import com.intellij.openapi.project.Project
import spp.jetbrains.marker.extend.LiveCommand
import spp.jetbrains.marker.extend.LiveCommandContext
import spp.jetbrains.marker.jvm.psi.EndpointDetector
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.PORTAL_OPENING
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.UPDATE_PORTAL_CONFIG

/**
 * Opens the 'Endpoint-Overview' dashboard via portal popup.
 */
class ViewOverviewCommand(project: Project) : LiveCommand(project) {
    override val name = message("view_overview")
    override val description = "<html><span style=\"font-size: 80%; color: ${getCommandTypeColor()}\">" +
            message("live_view") + " ➛ " + message("overview") + " ➛ " + message("scope") +
            ": </span><span style=\"font-size: 80%; color: ${getCommandHighlightColor()}\">" + message("class") +
            "</span></html>"
    override val selectedIcon = "view-overview/icons/view-overview_selected.svg"
    override val unselectedIcon = "view-overview/icons/view-overview_unselected.svg"

    override fun trigger(context: LiveCommandContext) {
        val endpointId = context.getUserData(EndpointDetector.ENDPOINT_ID.name) as String? ?: return
        val pageType = "Overview"
        val newPage = "/dashboard/GENERAL/Endpoint/${endpointId.substringBefore("_")}/$endpointId/Endpoint-$pageType?portal=true&fullview=true"

        context.triggerEvent(UPDATE_PORTAL_CONFIG, listOf("setPage", newPage)) {
            context.triggerEvent(PORTAL_OPENING, listOf(PORTAL_OPENING))
        }
    }
}

//registerCommand { ViewOverviewCommand(project!!) }
