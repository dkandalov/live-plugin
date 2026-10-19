@file:Suppress("FunctionName", "unused", "LocalVariableName")

package liveplugin.implementation.actions.mcp

import com.intellij.diagnostic.MessagePool
import com.intellij.idea.LoggerFactory
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
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import liveplugin.implementation.LivePlugin
import liveplugin.implementation.Settings
import liveplugin.implementation.pluginrunner.PluginRunner.Companion.runPluginsAndAwait
import liveplugin.implementation.pluginrunner.PluginRunner.Companion.unloadPlugins
import liveplugin.implementation.pluginrunner.RunningError
import liveplugin.implementation.pluginrunner.SetupError
import java.nio.file.Files
import kotlin.io.path.exists

class LivePluginToolset : McpToolset {
    override fun isEnabled(): Boolean = Settings.instance.mcpToolsEnabled

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
                    is SetupError -> error.message
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

    @McpTool
    @McpDescription("""
        |Reads the tail of the IDE's main log file (idea.log, same file as 'Help | Show Log in Finder').
        |Only the current log file is read, not rotated backups (idea.log.1, etc.).
    """)
    fun read_ide_log(
        @McpDescription("Case-insensitive substring to filter lines by. When set, returns the last matching lines from anywhere in the file, not just its tail.")
        filter: String? = null,
        @McpDescription("Maximum number of lines to return, from the end of the file (default: $DEFAULT_LOG_TAIL_LINES, max: $MAX_LOG_TAIL_LINES)")
        tail_lines: Int = DEFAULT_LOG_TAIL_LINES,
    ): String {
        val normalizedTailLines = tail_lines.coerceIn(1, MAX_LOG_TAIL_LINES)
        val logFile = LoggerFactory.getLogFilePath()
        if (!logFile.exists()) mcpFail("Log file not found: $logFile")

        val lastLines = ArrayDeque<String>(normalizedTailLines)
        Files.newBufferedReader(logFile).useLines { lines ->
            lines.forEach { line ->
                if (filter == null || line.contains(filter, ignoreCase = true)) {
                    if (lastLines.size == normalizedTailLines) lastLines.removeFirst()
                    lastLines.addLast(line.take(MAX_LOG_LINE_LENGTH))
                }
            }
        }

        return if (lastLines.isEmpty()) "No matching lines in $logFile" else lastLines.joinToString("\n")
    }

    @McpTool
    @McpDescription(
        """
        |Describes where to find a local checkout of the IntelliJ Platform source code and how to use it
        |to look up APIs (extension points, interfaces, default implementations, etc.) when writing LivePlugin scripts
        |or other plugin code. Call this tool first before guessing API signatures.
        |If no checkout is configured yet, returns instructions for cloning one and setting its path.
    """
    )
    fun get_intellij_source_code_path(): String {
        val path = Settings.instance.intellijSourceCodePath

        return if (isValidDirectory(Settings.instance.intellijSourceCodePath))
            """
                |IntelliJ Platform source code is available at: $path
                |This path points to a local git clone of https://github.com/JetBrains/intellij-community, so standard file search
                |and grep tools work directly against it.
                |
                |Use it to look up exact API signatures, extension point declarations (plugin.xml files), default implementations,
                |and Javadoc/KDoc comments instead of guessing them. For example:
                |- Search for an extension point by its qualified name (e.g. 'com.intellij.someExtensionPoint') across '*.xml' files
                |  to find where it's declared and what interface/class it expects.
                |- Search for a class or interface name to find its source file, see its full API, and read usage examples
                |  in other plugins within the same checkout.
                |- Search for a string like a notification group id or action id to see how existing plugins use a particular API.
            """.trimMargin()
        else
            """
                |No local checkout of IntelliJ Platform (intellij-community) source code is configured.
                |
                |To set it up:
                |1. Ask the user where they want to clone the repository to (e.g. a path next to their other projects).
                |2. Clone it there (a shallow clone is enough for source lookups):
                |   git clone --depth 1 https://github.com/JetBrains/intellij-community.git <chosen-path>
                |3. In the IDE, open the 'Live Plugins' tool window, click the toolbar's Settings dropdown,
                |   and select 'Set IntelliJ Source Code Path for MCP…', then point it at the cloned directory.
                |4. Call this tool again to get usage instructions.
            """.trimMargin()
    }

    private suspend fun syntheticActionEvent(): AnActionEvent {
        val project = currentCoroutineContext().projectOrNull
        val dataContext = SimpleDataContext.builder().apply {
            if (project != null) add(CommonDataKeys.PROJECT, project)
        }.build()
        return AnActionEvent.createEvent(dataContext, null, "", ActionUiKind.NONE, null)
    }
}

private const val DEFAULT_LOG_TAIL_LINES = 200
private const val MAX_LOG_TAIL_LINES = 2000
private const val MAX_LOG_LINE_LENGTH = 2000

fun isValidDirectory(path: String): Boolean =
    path.isNotBlank() && LocalFileSystem.getInstance().findFileByPath(path)?.isDirectory == true
