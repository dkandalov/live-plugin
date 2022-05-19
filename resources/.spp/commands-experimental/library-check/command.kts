import com.intellij.notification.NotificationType.ERROR
import io.vertx.core.json.JsonArray
import liveplugin.PluginUtil.*
import spp.command.*
import spp.jetbrains.sourcemarker.PluginUI.*
import spp.plugin.*

class LibraryCheckCommand : LiveCommand() {
    override val name = "library-check"
    override val description = "<html><span style=\"color: ${getCommandTypeColor()}\">" +
            "Find all jar libraries used in the currently active services" + "</span></html>"
    override val selectedIcon: String = "library-check/icons/library-check_selected.svg"
    override val unselectedIcon: String = "library-check/icons/library-check_unselected.svg"

    override suspend fun triggerSuspend(context: LiveCommandContext) {
        val librarySearch = (context.args.firstOrNull() ?: "").lowercase()
        val foundLibraries = mutableSetOf<String>()

        val activeServices = skywalkingMonitorService.getActiveServices()
        if (activeServices.isEmpty()) {
            show("Unable to find active services", notificationType = ERROR)
            return
        }
        val activeServiceInstances = activeServices.flatMap { skywalkingMonitorService.getServiceInstances(it.id) }
        if (activeServiceInstances.isEmpty()) {
            show("Unable to find active service instances", notificationType = ERROR)
            return
        }

        activeServiceInstances.forEach {
            val jarDependencies = it.attributes.find { it.name == "Jar Dependencies" }?.let { JsonArray(it.value) }
            jarDependencies?.let {
                foundLibraries.addAll(
                        jarDependencies.list.map { it.toString() }
                                .filter { it.lowercase().contains(librarySearch) }
                )
            }
        }

        val serviceCount = activeServices.size
        val instanceCount = activeServiceInstances.size
        showInConsole(
                foundLibraries,
                "Libraries Found (Services: $serviceCount - Instances: $instanceCount)",
                project
        )
    }
}

registerCommand(LibraryCheckCommand())
