import com.intellij.ide.util.DirectoryUtil
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.util.PsiNavigateUtil
import liveplugin.implementation.common.IdeUtil
import spp.plugin.*
import spp.command.*
import spp.jetbrains.sourcemarker.PluginUI.*
import spp.jetbrains.sourcemarker.PluginBundle.message

class NewCommandCommand : LiveCommand() {
    override val name = message("New Command")
    override val description = "<html><span style=\"font-size: 80%; color: ${getCommandTypeColor()}\">" +
            "Add new custom command" + "</span></html>"
    override val selectedIcon = "new-command/icons/new-command_selected.svg"
    override val unselectedIcon = "new-command/icons/new-command_unselected.svg"

    override fun trigger(context: LiveCommandContext) {
        if (context.args.isEmpty()) {
            show("Missing command name", notificationType = NotificationType.ERROR)
            return
        }

        runWriteAction {
            val commandName = context.args.joinToString(" ")
            val commandDir = commandName.replace(" ", "-")
            val psiFile = PsiFileFactory.getInstance(project).createFileFromText(
                    "command.kts", IdeUtil.kotlinFileType, getNewCommandScript(commandName)
            )
            val baseDirectory = PsiDirectoryFactory.getInstance(project).createDirectory(project.baseDir)
            val psiDirectory = DirectoryUtil.createSubdirectories(".spp/commands/$commandDir", baseDirectory, "/")

            PsiNavigateUtil.navigate(psiDirectory.add(psiFile))
        }
    }

    private fun getNewCommandScript(commandName: String): String {
        val properCommandName = commandName.split(" ", "-").map { it.capitalize() }.joinToString("")
        return """
            import spp.plugin.*
            import spp.command.*
            import spp.jetbrains.sourcemarker.PluginUI.*

            class ${properCommandName}Command : LiveCommand() {
                override val name = "$commandName"
                override val description = "<html><span style=\"color: ${'$'}{getCommandTypeColor()}\">" +
                        "My custom command" + "</span></html>"

                override fun trigger(context: LiveCommandContext) {
                    show("Hello world")
                }
            }

            registerCommand(${properCommandName}Command())
        """.trimIndent()
    }
}

registerCommand(NewCommandCommand())
