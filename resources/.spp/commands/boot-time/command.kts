import com.intellij.notification.NotificationType
import kotlinx.coroutines.runBlocking
import spp.command.LiveCommand
import spp.command.LiveCommandContext
import spp.jetbrains.sourcemarker.PluginUI.getCommandTypeColor
import spp.plugin.registerCommand
import spp.plugin.show
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class BootTimeCommand : LiveCommand() {

    override val name = "boot-time"
    override val description = "<html><span style=\"font-size: 90%; color: ${getCommandTypeColor()}\">" +
            "Gets the earliest boot time for the current service" + "</span></html>"
    override val selectedIcon: String = "boot-time/icons/boot-time_selected.svg"
    override val unselectedIcon: String = "boot-time/icons/boot-time_unselected.svg"

    override fun trigger(context: LiveCommandContext) = runBlocking {
        val serverTimezone = skywalkingMonitorService.getTimeInfo().result?.timezone
        if (serverTimezone == null) {
            show("Unable to determine server timezone", notificationType = NotificationType.ERROR)
            return@runBlocking
        }
        val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneOffset.ofHours(serverTimezone.toInt()))

        var startTime: LocalDateTime? = null
        skywalkingMonitorService.getActiveServices().forEach {
            skywalkingMonitorService.getServiceInstances(it.id).forEach {
                val instanceStartTime = it.attributes.find { it.name == "Start Time" }?.let {
                    ZonedDateTime.parse(it.value, timeFormatter).withZoneSameInstant(ZoneId.systemDefault())
                        .toLocalDateTime()
                }
                if (startTime == null || instanceStartTime?.isBefore(startTime) == true) {
                    startTime = instanceStartTime
                }
            }
        }

        if (startTime != null) {
            val duration = Duration.between(startTime, LocalDateTime.now())
            val prettyTimeAgo = String.format(
                "%d days, %d hours, %d minutes, %d seconds ago",
                duration.toDays(), duration.toHours() % 24, duration.toMinutes() % 60, duration.toSeconds() % 60
            )
            show("$prettyTimeAgo ($startTime)")
        } else {
            show("Unable to find active service(s)", notificationType = NotificationType.ERROR)
        }
    }
}

registerCommand(BootTimeCommand())
