package liveplugin.implementation.common

import com.intellij.diagnostic.PluginException
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.execution.ui.actions.CloseAction
import com.intellij.icons.AllIcons
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType.ERROR
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.AsyncDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.InputValidatorEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.ReflectionUtil
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Icon
import javax.swing.JPanel

const val livePluginId = "LivePlugin"

// Lazy because it seems that it can be initialised before notification group is initialised in plugin.xml
val livePluginNotificationGroup by lazy {
    NotificationGroupManager.getInstance().getNotificationGroup("Live Plugin")!!
}

object IdeUtil {
    const val ideStartupActionPlace = "IDE_STARTUP"
    const val livePluginActionPlace = "LIVE_PLUGIN"

    val textFileType: FileType = PlainTextFileType.INSTANCE
    val groovyFileType = FileTypeManager.getInstance().getFileTypeByExtension("groovy")
    val kotlinFileType = KotlinScriptFileType
    val xmlFileType = FileTypeManager.getInstance().getFileTypeByExtension("xml")

    val logger = Logger.getInstance(livePluginId)

    fun displayError(consoleTitle: String, text: String, project: Project?) {
        if (project == null) {
            // "project" can be null when there are no open projects or while IDE is loading.
            // It is important to log error specifying plugin id, otherwise IDE will try to guess
            // plugin id based on classes in stacktrace and might get it wrong,
            // e.g. if activity tracker plugin is installed, it will include LivePlugin classes as library
            // (see com.intellij.diagnostic.IdeErrorsDialog.findPluginId)
            logger.error(consoleTitle, PluginException(text, PluginId.getId(livePluginId)))
        } else {
            project.showInConsole(text, consoleTitle, ConsoleViewContentType.ERROR_OUTPUT)
        }
    }

    fun Project?.showError(message: String, e: Exception? = null) {
        livePluginNotificationGroup.createNotification(title = "Live plugin", message, ERROR).notify(this)
        if (e != null) logger.info(e) // Don't log it as an error because then IJ will show an additional window with stacktrace.
    }

    fun Project?.showInputDialog(message: String, title: String, inputValidator: InputValidatorEx? = null, initialValue: String? = null) =
        Messages.showInputDialog(this, message, title, null, initialValue, inputValidator)

    fun runLaterOnEdt(f: () -> Any) {
        ApplicationManager.getApplication().invokeLater { f.invoke() }
    }

    fun <T> runOnEdt(f: () -> T): T {
        val result = AtomicReference<T>()
        ApplicationManager.getApplication().invokeAndWait({ result.set(f()) }, ModalityState.NON_MODAL)
        return result.get()
    }

    @JvmStatic fun unscrambleThrowable(throwable: Throwable): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return Unscramble.normalizeText(writer.buffer.toString())
    }

    private fun Project.showInConsole(message: String, consoleTitle: String, contentType: ConsoleViewContentType) {
        ToolWindowManager.getInstance(this).invokeLater {
            val runContentManager = RunContentManager.getInstance(this)
            val executor = DefaultRunExecutor.getRunExecutorInstance()

            val toolbarActions = DefaultActionGroup()
            val consoleView = TextConsoleBuilderFactory.getInstance().createBuilder(this).console.also {
                it.print(message, contentType)
            }
            val consoleComponent = MyConsolePanel(consoleView, toolbarActions)

            val contentDescriptor = runContentManager.allDescriptors.find { it.displayName == consoleTitle }
            if (contentDescriptor != null) runContentManager.removeRunContent(executor, contentDescriptor)

            val descriptor = object : RunContentDescriptor(consoleView, null, consoleComponent, consoleTitle) {
                override fun isContentReuseProhibited() = true
                override fun getIcon() = AllIcons.Nodes.Plugin
            }
            toolbarActions.add(CloseAction(executor, descriptor, this))
            toolbarActions.addAll(*consoleView.createConsoleActions())

            runContentManager.showRunContent(executor, descriptor)
        }
    }

    private class MyConsolePanel(consoleView: ExecutionConsole, toolbarActions: ActionGroup) : JPanel(BorderLayout()) {
        init {
            val toolbarPanel = JPanel(BorderLayout()).also {
                val actionToolbar = ActionManager.getInstance().createActionToolbar(livePluginActionPlace, toolbarActions, false)
                actionToolbar.targetComponent = this
                it.add(actionToolbar.component)
            }
            add(toolbarPanel, BorderLayout.WEST)
            add(consoleView.component, BorderLayout.CENTER)
        }
    }


    /**
     * Copy-pasted from `UnscrambleDialog#normalizeText(String)`
     * because PhpStorm doesn't have this class.
     */
    private object Unscramble {
        fun normalizeText(@NonNls text: String): String {
            val lines = text
                .replace("(\\S[ \\t\\x0B\\f\\r]+)(at\\s+)".toRegex(), "$1\n$2")
                .split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            val builder = StringBuilder(text.length)
            var first = true
            var inAuxInfo = false
            for (line in lines) {

                if (!inAuxInfo && (line.startsWith("JNI global references") || line.trim { it <= ' ' } == "Heap")) {
                    builder.append("\n")
                    inAuxInfo = true
                }
                if (inAuxInfo) {
                    builder.append(trimSuffix(line)).append("\n")
                    continue
                }
                if (!first && mustHaveNewLineBefore(line)) {
                    builder.append("\n")
                    if (line.startsWith("\"")) builder.append("\n") // Additional line break for thread names
                }
                first = false
                val i = builder.lastIndexOf("\n")
                val lastLine = if (i == -1) builder else builder.subSequence(i + 1, builder.length)
                if (lastLine.toString().matches("\\s*at".toRegex()) && !line.matches("\\s+.*".toRegex())) {
                    builder.append(" ") // separate 'at' from file name
                }
                builder.append(trimSuffix(line))
            }
            return builder.toString()
        }

        @Suppress("NAME_SHADOWING")
        private fun mustHaveNewLineBefore(line: String): Boolean {
            var line = line
            val nonWs = CharArrayUtil.shiftForward(line, 0, " \t")
            if (nonWs < line.length) {
                line = line.substring(nonWs)
            }

            if (line.startsWith("at")) return true        // Start of the new stack frame entry
            if (line.startsWith("Caused")) return true    // Caused by message
            if (line.startsWith("- locked")) return true  // "Locked a monitor" logging
            if (line.startsWith("- waiting")) return true // "Waiting for monitor" logging
            if (line.startsWith("- parking to wait")) return true
            if (line.startsWith("java.lang.Thread.State")) return true
            return line.startsWith("\"")        /* Start of the new thread (thread name)*/
        }

        private fun trimSuffix(line: String): String {
            var len = line.length

            while (0 < len && line[len - 1] <= ' ') {
                len--
            }
            return if (len < line.length) line.substring(0, len) else line
        }
    }


    /**
     * Can't use `FileTypeManager.getInstance().getFileTypeByExtension("kts");` here
     * because it will return FileType for .kt files and this will cause creating files with wrong extension.
     */
    object KotlinScriptFileType : FileType {
        override fun getName() = "Kotlin"
        override fun getDescription() = this.name
        override fun getDefaultExtension() = "kts"
        override fun getIcon() = kotlinScriptIcon
        override fun isBinary() = false
        override fun isReadOnly() = false
        override fun getCharset(virtualFile: VirtualFile, bytes: ByteArray): String? = null

        private val kotlinScriptIcon by lazy {
            findIconOrNull("/org/jetbrains/kotlin/idea/icons/kotlin_file.svg") ?: AllIcons.FileTypes.Text
        }

        // The kotlin icon is missing in some IDEs like WebStorm, so it's important
        // to set `strict` to false in findIcon, so an exception won't be thrown.
        private fun findIconOrNull(path: String): Icon? {
            val callerClass = ReflectionUtil.getGrandCallerClass() ?: return null
            return IconLoader.findIcon(path, callerClass, deferUrlResolve = false, strict = false)
        }
    }
}

fun inputValidator(f: (String) -> String?) =
    InputValidatorEx { inputString -> f(inputString) }

fun AnActionEvent.selectedFiles(): List<FilePath> =
    dataContext.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.map { it.toFilePath() } ?: emptyList()

@Suppress("UnstableApiUsage")
class MapDataContext(val map: Map<String, Any?>) : DataContext, AsyncDataContext {
    override fun getData(dataId: String) = map[dataId]
}

private const val requestor = livePluginId

fun createFile(parentPath: String, fileName: String, text: String, whenCreated: (VirtualFile) -> Unit = {}) {
    runIOAction("createFile") {
        val parentFolder = VfsUtil.createDirectoryIfMissing(parentPath) ?: throw IOException("Failed to create folder $parentPath")
        if (parentFolder.findChild(fileName) == null) {
            val virtualFile = parentFolder.createChildData(requestor, fileName)
            VfsUtil.saveText(virtualFile, text)
            whenCreated(virtualFile)
        }
    }
}

fun VirtualFile.delete() {
    runIOAction("delete") {
        this.delete(requestor)
    }
}

private fun runIOAction(actionName: String, f: () -> Unit) {
    var exception: IOException? = null
    runWriteAction {
        CommandProcessor.getInstance().executeCommand(null, {
            try {
                f()
            } catch (e: IOException) {
                exception = e
            }
        }, actionName, livePluginId)
    }
    exception?.let { throw it }
}
