package liveplugin

import com.intellij.diagnostic.PluginException
import com.intellij.execution.ExecutionManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.actions.CloseAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Messages.showOkCancelDialog
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.ReflectionUtil
import com.intellij.util.containers.ContainerUtil.map
import com.intellij.util.download.DownloadableFileService
import com.intellij.util.text.CharArrayUtil
import liveplugin.LivePluginAppComponent.Companion.livePluginId
import liveplugin.pluginrunner.AnError
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import java.io.PrintWriter
import java.io.StringWriter
import javax.swing.Icon
import javax.swing.JPanel

object IdeUtil {
    const val ideStartupActionPlace = "IDE_STARTUP"

    val groovyFileType = FileTypeManager.getInstance().getFileTypeByExtension(".groovy")
    val kotlinFileType = KotlinScriptFileType.instance
    val dummyDataContext = DataContext { null }

    private val logger = Logger.getInstance(IdeUtil::class.java)

    fun displayError(error: AnError, project: Project?) {
        val (title, message) = when (error) {
            is AnError.LoadingError -> Pair("Loading error", error.message + if (error.throwable != null) "\n" + unscrambleThrowable(error.throwable) else "")
            is AnError.RunningError -> Pair("Running error", unscrambleThrowable(error.throwable))
        }
        displayError(title, message, project)
    }

    private fun displayError(consoleTitle: String, text: String, project: Project?) {
        if (project == null) {
            // "project" can be null when there are no open projects or while IDE is loading.
            // It is important to log error specifying plugin id, otherwise IDE will try to guess
            // plugin id based on classes in stacktrace and might get it wrong,
            // e.g. if activity tracker plugin is installed, it will include LivePlugin classes as library
            // (see com.intellij.diagnostic.IdeErrorsDialog.findPluginId)
            logger.error(consoleTitle, PluginException(text, PluginId.getId(livePluginId)))
        } else {
            showInConsole(text, consoleTitle, project, ERROR_OUTPUT)
        }
    }

    fun showErrorDialog(project: Project?, message: String, title: String) {
        Messages.showMessageDialog(project, message, title, Messages.getErrorIcon())
    }

    fun saveAllFiles() {
        ApplicationManager.getApplication().runWriteAction { FileDocumentManager.getInstance().saveAllDocuments() }
    }

    fun performAction(action: AnAction, place: String) {
        val event = AnActionEvent(
            null,
            dummyDataContext,
            place,
            action.templatePresentation,
            ActionManager.getInstance(),
            0
        )
        invokeLaterOnEDT { action.actionPerformed(event) }
    }

    fun isOnClasspath(className: String): Boolean {
        val resource = IdeUtil::class.java.classLoader.getResource(className.replace(".", "/") + ".class")
        return resource != null
    }

    fun askIfUserWantsToRestartIde(message: String) {
        val answer = showOkCancelDialog(message, "Restart Is Required", "Restart", "Postpone", Messages.getQuestionIcon())
        if (answer == Messages.OK) {
            ApplicationManagerEx.getApplicationEx().restart(true)
        }
    }

    fun downloadFile(downloadUrl: String, fileName: String, targetPath: String): Boolean =
        downloadFiles(listOf(Pair(downloadUrl, fileName)), targetPath)

    fun invokeLaterOnEDT(f: () -> Any) {
        ApplicationManager.getApplication().invokeLater { f.invoke() }
    }

    // TODO make download non-modal
    private fun downloadFiles(urlAndFileNames: List<Pair<String, String>>, targetPath: String): Boolean {
        val service = DownloadableFileService.getInstance()
        val descriptions = map(urlAndFileNames) { service.createFileDescription(it.first + it.second, it.second) }
        val files = service.createDownloader(descriptions, "").downloadFilesWithProgress(targetPath, null, null)
        return files != null && files.size == urlAndFileNames.size
    }

    @JvmStatic fun unscrambleThrowable(throwable: Throwable): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return Unscramble.normalizeText(writer.buffer.toString())
    }

    private fun showInConsole(message: String, consoleTitle: String, project: Project, contentType: ConsoleViewContentType) {
        val runnable = {
            val console = TextConsoleBuilderFactory.getInstance().createBuilder(project).console
            console.print(message, contentType)

            val toolbarActions = DefaultActionGroup()
            val consoleComponent = MyConsolePanel(console, toolbarActions)
            val descriptor = object: RunContentDescriptor(console, null, consoleComponent, consoleTitle) {
                override fun isContentReuseProhibited(): Boolean = true
                override fun getIcon(): Icon? = AllIcons.Nodes.Plugin
            }
            val executor = DefaultRunExecutor.getRunExecutorInstance()

            toolbarActions.add(CloseAction(executor, descriptor, project))
            toolbarActions.addAll(*console.createConsoleActions())

            ExecutionManager.getInstance(project).contentManager.showRunContent(executor, descriptor)
        }
        ApplicationManager.getApplication().invokeAndWait(runnable, ModalityState.NON_MODAL)
    }

    private class MyConsolePanel internal constructor(consoleView: ExecutionConsole, toolbarActions: ActionGroup): JPanel(BorderLayout()) {
        init {
            val toolbarPanel = JPanel(BorderLayout())
            toolbarPanel.add(ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarActions, false).component)
            add(toolbarPanel, BorderLayout.WEST)
            add(consoleView.component, BorderLayout.CENTER)
        }
    }


    /**
     * Copy-pasted from `UnscrambleDialog#normalizeText(String)`
     * because PhpStorm doesn't have this class.
     */
    private object Unscramble {
        internal fun normalizeText(@NonNls text: String): String {
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
     * Can't use `FileTypeManager.getInstance().getFileTypeByExtension(".kts");` here
     * because it will return FileType for .kt files and this will cause creating files with wrong extension.
     */
    class KotlinScriptFileType: FileType {
        override fun getName(): String = "Kotlin"
        override fun getDescription(): String = this.name
        override fun getDefaultExtension(): String = "kts"
        override fun getIcon(): Icon? = kotlinScriptIcon
        override fun isBinary(): Boolean = false
        override fun isReadOnly(): Boolean = false
        override fun getCharset(virtualFile: VirtualFile, bytes: ByteArray): String? = null

        companion object {
            internal val instance: FileType = KotlinScriptFileType()

            // The kotlin icon is missing in some IDEs like WebStorm, so it's important
            // to set `strict` to false in findIcon, so an exception won't be thrown.
            private fun findIconOrNull(path: String) : Icon? {
                val callerClass = ReflectionUtil.getGrandCallerClass() ?: return null
                return IconLoader.findIcon(path, callerClass, false, false)
            }

            private val kotlinScriptIcon by lazy {
                findIconOrNull("/org/jetbrains/kotlin/idea/icons/kotlin_file.png") ?: AllIcons.FileTypes.Text
            }
        }
    }
}

fun String.findFileByUrl(): VirtualFile? = VirtualFileManager.getInstance().findFileByUrl("file:///$this")

fun String.refreshAndFindFileByUrl(): VirtualFile? = VirtualFileManager.getInstance().refreshAndFindFileByUrl("file:///$this")
