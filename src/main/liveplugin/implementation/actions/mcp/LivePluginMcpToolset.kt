@file:Suppress("FunctionName", "unused")

package liveplugin.implementation.actions.mcp

import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.mcpFail
import com.intellij.mcpserver.projectOrNull
import com.intellij.notification.ActionCenter
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import liveplugin.implementation.LivePlugin
import liveplugin.implementation.pluginrunner.PluginRunner.Companion.runPluginsAndAwait
import liveplugin.implementation.pluginrunner.PluginRunner.Companion.unloadPlugins
import liveplugin.implementation.pluginrunner.RunningError
import liveplugin.implementation.pluginrunner.SetupError

class LivePluginMcpToolset : McpToolset {
    @McpTool
    @McpDescription("""
        |Lists ids of LivePlugin scripts available in the 'live-plugins' folder (Settings | Tools | Live Plugins tool window).
        |Each id is the plugin's folder name. Use these ids with 'run_live_plugin' or 'unload_live_plugin'.
    """)
    suspend fun list_live_plugins(): String {
        val ids = LivePlugin.livePluginsById().keys.sorted()
        return if (ids.isEmpty()) "No LivePlugin scripts found in the 'live-plugins' folder" else ids.joinToString("\n")
    }

    @McpTool
    @McpDescription("""
        |Runs a LivePlugin script in the current IDE process without restarting the IDE.
        |If the plugin is already running, it's unloaded first and then run again (i.e. reloaded) so that code changes take effect.
        |If 'plugin_id' is omitted, all LivePlugin scripts in the 'live-plugins' folder are run/reloaded.
        |Use 'list_live_plugins' to find out available plugin ids.
    """)
    suspend fun run_live_plugin(
        @McpDescription("Id (folder name) of the LivePlugin script to run. If omitted, all available plugins are run.")
        plugin_id: String? = null,
    ): String {
        val pluginsById = LivePlugin.livePluginsById()
        val livePlugins =
            if (plugin_id == null) pluginsById.values.toList()
            else listOf(
                pluginsById[plugin_id]
                    ?: mcpFail("No LivePlugin found with id '$plugin_id'. Known ids: ${pluginsById.keys.sorted()}")
            )
        if (livePlugins.isEmpty()) return "No LivePlugin scripts found in the 'live-plugins' folder"

        val event = syntheticActionEvent()
        val errorByPluginId = withContext(Dispatchers.IO) { runPluginsAndAwait(livePlugins, event) }

        if (errorByPluginId.isEmpty()) {
            return "Successfully ran: ${livePlugins.joinToString(", ") { it.id }}"
        }
        return buildString {
            val succeeded = livePlugins.map { it.id } - errorByPluginId.keys
            if (succeeded.isNotEmpty()) appendLine("Successfully ran: ${succeeded.joinToString(", ")}")
            errorByPluginId.forEach { (id, error) ->
                val message = when (error) {
                    is SetupError   -> error.message
                    is RunningError -> error.throwable.message ?: error.throwable.toString()
                }
                appendLine("Failed to run '$id': $message")
            }
        }.trim()
    }

    @McpTool
    @McpDescription("Unloads a currently running LivePlugin script, disposing its resources without restarting the IDE.")
    suspend fun unload_live_plugin(
        @McpDescription("Id (folder name) of the LivePlugin script to unload.")
        plugin_id: String,
    ): String {
        val livePlugin = LivePlugin.livePluginsById()[plugin_id] ?: mcpFail("No LivePlugin found with id '$plugin_id'.")
        unloadPlugins(listOf(livePlugin))
        return "Unloaded '$plugin_id'"
    }

    @McpTool
    @McpDescription("""
        |Lists notifications shown in the IDE's 'Notifications' tool window for the current project
        |(or application-wide notifications if no project is open), most recent first.
        |'content' may contain simple HTML (e.g. links).
    """)
    suspend fun list_notifications(
        @McpDescription("Maximum number of notifications to return (default: 200)")
        limit: Int = 200,
    ): String {
        val project = currentCoroutineContext().projectOrNull
        val notifications = ActionCenter.getNotifications(project)
            .sortedByDescending { it.timestamp }
            .take(limit)
        if (notifications.isEmpty()) return "No notifications"

        return notifications.joinToString("\n\n") {
            buildString {
                appendLine("[${it.type}] ${it.groupId}${if (it.isExpired) " (expired)" else ""}")
                if (it.title.isNotEmpty()) appendLine("Title: ${it.title}")
                it.subtitle?.let { subtitle -> appendLine("Subtitle: $subtitle") }
                appendLine("Timestamp: ${it.timestamp}")
                append("Content: ${it.content}")
            }
        }
    }

    private suspend fun syntheticActionEvent(): AnActionEvent {
        val project = currentCoroutineContext().projectOrNull
        val dataContext = SimpleDataContext.builder().apply {
            if (project != null) add(CommonDataKeys.PROJECT, project)
        }.build()
        return AnActionEvent.createEvent(dataContext, null, "", ActionUiKind.NONE, null)
    }
}
