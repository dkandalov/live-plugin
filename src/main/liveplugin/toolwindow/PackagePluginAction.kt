package liveplugin.toolwindow

import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages.CANCEL
import com.intellij.openapi.ui.Messages.showOkCancelDialog
import com.intellij.openapi.util.NlsContexts.*
import com.intellij.util.io.Compressor
import liveplugin.Icons
import liveplugin.LivePluginAppComponent.Companion.livePluginNotificationGroup
import liveplugin.LivePluginAppComponent.Companion.readSampleScriptFile
import liveplugin.LivePluginPaths
import liveplugin.pluginrunner.LivePlugin
import liveplugin.pluginrunner.canBeHandledBy
import liveplugin.pluginrunner.kotlin.KotlinPluginRunner
import liveplugin.pluginrunner.kotlin.SrcHashCode
import liveplugin.pluginrunner.kotlin.SrcHashCode.Companion.hashFileName
import liveplugin.pluginrunner.selectedFilePaths
import liveplugin.pluginrunner.toLivePlugins
import liveplugin.toFilePath
import liveplugin.toolwindow.popup.NewPluginXmlScript
import java.io.ByteArrayInputStream
import java.util.jar.Manifest

class PackagePluginAction: AnAction(
    "Package Plugin",
    "Package selected plugins so they can be uploaded to plugins marketplace",
    Icons.packagePluginIcon
), DumbAware {
    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.selectedFilePaths().canBeHandledBy(listOf(KotlinPluginRunner.main))
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        event.selectedFilePaths().toLivePlugins().forEach { packagePlugin(it, project) }
    }

    private fun packagePlugin(plugin: LivePlugin, project: Project) {
        val pluginXml = plugin.path + "plugin.xml"
        if (!pluginXml.exists()) {
            val fileContent = readSampleScriptFile("${LivePluginPaths.kotlinExamplesPath}/plugin.xml")
                .replaceFirst("com.your.company.unique.plugin.id", plugin.id)
                .replaceFirst("TODO Plugin Name", plugin.id.replace('-', ' ').split(' ').filter { it.isNotEmpty() }.joinToString(" ") { it.capitalize() })
                .replaceFirst("Your name", System.getProperty("user.name"))
            NewPluginXmlScript(fileContent).createNewFile(project, plugin.path.toVirtualFile() ?: error("Can't create virtual file for '${plugin.path.value}'"))
            val message = "Please review and <a href=\"\">edit its content</a> before publishing the plugin."
            val listener = NotificationListener { _, _ ->
                val virtualFile = pluginXml.toVirtualFile() ?: return@NotificationListener
                FileEditorManager.getInstance(project).openFile(virtualFile, true, true)
            }
            livePluginNotificationGroup.createNotification("Created plugin.xml", message, INFORMATION, listener).notify(project)
        }

        val compilerOutput = LivePluginPaths.livePluginsCompiledPath + plugin.id
        val livePluginJar = LivePluginPaths.livePluginLibPath + "LivePlugin.jar"

        val jarFile = (plugin.path + "${plugin.id}.jar").toFile()
        val zipFile = (plugin.path + "${plugin.id}.zip").toFile()
        val files = listOf(jarFile, zipFile).filter { it.exists() }
        if (files.isNotEmpty()) {
            val message =
                if (files.size == 1) "File ${files.first().name} already exists. Do you want to continue and overwrite it?"
                else "Files ${files.joinToString { it.name }} already exist. Do you want to continue and overwrite them?"
            if (showOkCancelDialog(project, message, "Package Plugin", "Ok", "Cancel", null) == CANCEL) return
        }

        ProgressManager.getInstance().run(object: Task.Backgroundable(project, "Packaging ${plugin.id}", false, ALWAYS_BACKGROUND) {
            override fun run(indicator: ProgressIndicator) {
                if (SrcHashCode(plugin.path, compilerOutput).needsUpdate()) {
                    KotlinPluginRunner.main.setup(plugin)
                }
                if (!livePluginJar.exists()) error("Couldn't find '${livePluginJar.value}'")

                Compressor.Jar(jarFile).use { jar ->
                    jar.addManifest(Manifest(ByteArrayInputStream("Manifest-Version: 1.0\n".toByteArray())))
                    jar.addFile("META-INF/plugin.xml", pluginXml.toFile())
                    compilerOutput.allFiles()
                        .filterNot { it.name == hashFileName }
                        .forEach { filePath ->
                            val relativePath = filePath.value.removePrefix(compilerOutput.value + "/")
                            jar.addFile(relativePath, filePath.toFile())
                        }
                    // Include source code and other files (e.g. if there are some resources required by the plugin)
                    plugin.path.allFiles()
                        .filterNot { it == pluginXml || it == jarFile.toFilePath() || it == zipFile.toFilePath() }
                        .forEach { filePath ->
                            val relativePath = filePath.value.removePrefix(plugin.path.value + "/")
                            jar.addFile(relativePath, filePath.toFile())
                        }
                }

                Compressor.Zip(zipFile).use { zip ->
                    val libDir = plugin.id + "/lib"
                    zip.addDirectory(libDir)
                    zip.addFile("$libDir/${jarFile.name}", jarFile)
                    zip.addFile("$libDir/${livePluginJar.name}", livePluginJar.toFile()) // TODO remove unnecessary files from LivePlugin.jar
                }

                jarFile.delete()
            }
        })
    }
}
