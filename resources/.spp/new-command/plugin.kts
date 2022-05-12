import com.intellij.ide.util.DirectoryUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.util.PsiNavigateUtil
import liveplugin.implementation.common.IdeUtil
import liveplugin.*
import spp.jetbrains.marker.extend.LiveCommand
import spp.jetbrains.marker.extend.LiveCommandContext

class NewCommandCommand : LiveCommand() {
    override val name = message("New Command")
    override val description = "<html><span style=\"font-size: 80%; color: ${getCommandTypeColor()}\">" +
            "Add new custom command" + "</span></html>"
    override val selectedIcon = "new-command/icons/new-command_selected.svg"
    override val unselectedIcon = "new-command/icons/new-command_unselected.svg"

    override fun trigger(context: LiveCommandContext) {
        runWriteAction {
            if (context.args.isEmpty()) {
                show("Missing command name", notificationType = NotificationType.ERROR)
                return@runWriteAction
            }

            val commandName = context.args.joinToString(" ")
            val commandDir = commandName.replace(" ", "-")
            val psiFile = PsiFileFactory.getInstance(project).createFileFromText(
                    "plugin.kts", IdeUtil.kotlinFileType, getNewCommandScript(commandName)
            )
            val baseDirectory = PsiDirectoryFactory.getInstance(project).createDirectory(project.baseDir)
            val psiDirectory = DirectoryUtil.createSubdirectories(".spp/$commandDir", baseDirectory, "/")

            PsiNavigateUtil.navigate(psiDirectory.add(psiFile))
        }
    }

    private fun getNewCommandScript(commandName: String): String {
        val properCommandName = commandName.split(" ", "-").map { it.capitalize() }.joinToString("")
        return """
            import liveplugin.*
            import spp.jetbrains.marker.extend.*

            class ${properCommandName}Command : LiveCommand() {
                override val name = "$commandName"
                override val description = "<html><span style=\"font-size: 80%; color: ${'$'}{getCommandTypeColor()}\">" +
                        "My custom command" + "</span></html>"

                override fun trigger(context: LiveCommandContext) {
                    show("Hello world")
                }
            }

            registerCommand { ${properCommandName}Command() }
        """.trimIndent()
    }
}

registerCommand { NewCommandCommand() }
