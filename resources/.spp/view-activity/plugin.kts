import com.intellij.openapi.project.Project
import liveplugin.*
import spp.jetbrains.marker.extend.LiveCommand
import spp.jetbrains.marker.extend.LiveCommandContext
import spp.jetbrains.marker.jvm.psi.EndpointDetector
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.PORTAL_OPENING
import spp.jetbrains.marker.source.mark.api.event.SourceMarkEventCode.UPDATE_PORTAL_CONFIG

/**
 * Opens the 'Endpoint-Activity' dashboard via portal popup.
 */
class ViewActivityCommand(project: Project) : LiveCommand(project) {
    override val name = message("view_activity")
    override val description = "<html><span style=\"font-size: 80%; color: ${getCommandTypeColor()}\">" +
            message("live_view") + " ➛ " + message("activity") + " ➛ " + message("scope") +
            ": </span><span style=\"font-size: 80%; color: ${getCommandHighlightColor()}\">" + message("method") +
            "</span></html>"
    override val selectedIcon = "view-activity/icons/view-activity_selected.svg"
    override val unselectedIcon = "view-activity/icons/view-activity_unselected.svg"

    override fun trigger(context: LiveCommandContext) {
        val endpointId = context.getUserData(EndpointDetector.ENDPOINT_ID.name) as String? ?: return
        val pageType = "Activity"
        val newPage = "/dashboard/GENERAL/Endpoint/${endpointId.substringBefore("_")}/$endpointId/Endpoint-$pageType?portal=true&fullview=true"

        context.triggerEvent(UPDATE_PORTAL_CONFIG, listOf("setPage", newPage)) {
            context.triggerEvent(PORTAL_OPENING, listOf(PORTAL_OPENING))
        }
    }
}

registerCommand { ViewActivityCommand(project!!) }
