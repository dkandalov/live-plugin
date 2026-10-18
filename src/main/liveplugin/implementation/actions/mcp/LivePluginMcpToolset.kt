@file:Suppress("FunctionName", "unused", "LocalVariableName")

package liveplugin.implementation.actions.mcp

import com.intellij.diagnostic.MessagePool
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
    fun list_live_plugins(): String {
        val ids = LivePlugin.livePluginsById().keys.sorted()
        return if (ids.isEmpty()) "No LivePlugin scripts found" else ids.joinToString("\n")
    }

    @McpTool
    @McpDescription("""
        |Runs a LivePlugin script in the current IDE process without restarting the IDE.
        |If the plugin is already running, it's unloaded first and then run again (i.e. reloaded) so that code changes take effect.
        |Use 'list_live_plugins' to find out available plugin ids.
    """)
    suspend fun run_live_plugin(
        @McpDescription("Id (folder name) of the LivePlugin script to run.")
        plugin_id: String,
    ): String {
        val pluginsById = LivePlugin.livePluginsById()
        val livePlugin = pluginsById[plugin_id] ?: mcpFail("No LivePlugin found with id '$plugin_id'. Known ids: ${pluginsById.keys.sorted()}")
        val livePlugins = listOf(livePlugin)

        val event = syntheticActionEvent()
        val errorById = withContext(Dispatchers.IO) { runPluginsAndAwait(livePlugins, event) }

        return if (errorById.isEmpty()) {
            "Successfully ran: ${livePlugins.joinToString(", ") { it.id }}"
        } else buildString {
            val succeeded = livePlugins.map { it.id } - errorById.keys
            if (succeeded.isNotEmpty()) appendLine("Successfully ran: ${succeeded.joinToString(", ")}")
            errorById.forEach { (id, error) ->
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
    fun unload_live_plugin(
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

        return if (notifications.isEmpty()) "No notifications"
        else notifications.joinToString("\n\n") {
            listOfNotNull(
                "[${it.type}] ${it.groupId}${if (it.isExpired) " (expired)" else ""}",
                if (it.title.isNotEmpty()) "Title: ${it.title}" else null,
                it.subtitle?.let { subtitle -> if (subtitle.isNotBlank()) "Subtitle: $subtitle" else null },
                "Timestamp: ${it.timestamp}",
                "Content: ${it.content}"
            ).joinToString("\n")
        }
    }

    @McpTool
    @McpDescription("""
        |Lists IDE-internal fatal errors shown in the IDE's error widget/dialog (the 'Report IDE Errors' icon in the status bar).
        |These are IDE/plugin exceptions caught by the platform, not code inspection problems. Most recent first.
    """)
    @Suppress("UnstableApiUsage")
    fun list_ide_errors(
        @McpDescription("Maximum number of errors to return (default: 20)")
        limit: Int = 20,
        @McpDescription("Maximum stack trace lines per error (default: 50)")
        max_stack_trace_lines: Int = 50,
    ): String {
        val errors = MessagePool.getInstance().getFatalErrors(true, true)
            .sortedByDescending { it.date }
            .take(limit)

        return if (errors.isEmpty()) "No IDE errors"
        else errors.joinToString("\n\n") {
            val stackTraceLines = it.throwableText.lineSequence().take(max_stack_trace_lines).toList()
            val stackTrace = stackTraceLines.joinToString("\n") +
                if (stackTraceLines.size == max_stack_trace_lines) "\n... (truncated)" else ""

            listOfNotNull(
                "Date: ${it.date}${if (it.isRead) " (read)" else ""}${if (it.isSubmitted) " (submitted)" else ""}",
                it.message?.takeIf { message -> message.isNotBlank() }?.let { message -> "Message: $message" },
                stackTrace
            ).joinToString("\n")
        }
    }

    @McpTool
    @McpDescription("Clears all IDE-internal fatal errors from the IDE's error widget/dialog.")
    @Suppress("UnstableApiUsage")
    fun clear_ide_errors(): String {
        val count = MessagePool.getInstance().getFatalErrors(true, true).size
        MessagePool.getInstance().clearErrors()
        return "Cleared $count IDE error(s)"
    }

    private suspend fun syntheticActionEvent(): AnActionEvent {
        val project = currentCoroutineContext().projectOrNull
        val dataContext = SimpleDataContext.builder().apply {
            if (project != null) add(CommonDataKeys.PROJECT, project)
        }.build()
        return AnActionEvent.createEvent(dataContext, null, "", ActionUiKind.NONE, null)
    }
}
