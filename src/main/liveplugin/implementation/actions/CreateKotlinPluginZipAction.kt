package liveplugin.implementation.actions

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationListener.URL_OPENING_LISTENER
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.util.io.Compressor
import com.intellij.util.io.zip.JBZipFile
import liveplugin.implementation.LivePlugin
import liveplugin.implementation.LivePluginPaths.kotlinExamplesPath
import liveplugin.implementation.LivePluginPaths.livePluginLibPath
import liveplugin.implementation.LivePluginPaths.livePluginsCompiledPath
import liveplugin.implementation.actions.toolwindow.NewPluginXmlScript
import liveplugin.implementation.common.FilePath
import liveplugin.implementation.common.Icons.packagePluginIcon
import liveplugin.implementation.common.livePluginNotificationGroup
import liveplugin.implementation.livePlugins
import liveplugin.implementation.pluginrunner.PluginRunner.Companion.canBeHandledBy
import liveplugin.implementation.pluginrunner.kotlin.KotlinPluginRunner.Companion.mainKotlinPluginRunner
import liveplugin.implementation.pluginrunner.kotlin.SrcHashCode
import liveplugin.implementation.pluginrunner.kotlin.SrcHashCode.Companion.hashFileName
import liveplugin.implementation.readSampleScriptFile
import java.io.ByteArrayInputStream
import java.util.*
import java.util.jar.Manifest

class CreateKotlinPluginZipAction : AnAction(
    "Create Kotlin Plugin Zip",
    "Package selected live plugin into zip so that it can be uploaded to plugins marketplace",
    packagePluginIcon
), DumbAware {
    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.livePlugins().canBeHandledBy(listOf(mainKotlinPluginRunner))
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        event.livePlugins().forEach { packagePlugin(it, project) }
    }

    override fun getActionUpdateThread() = BGT

    private fun packagePlugin(plugin: LivePlugin, project: Project) {
        val pluginXml = plugin.path + "plugin.xml"
        val pluginJar = plugin.path + "${plugin.id}.jar"
        val livePluginJar = livePluginLibPath + "live-plugin.jar"
        val livePluginTrimmedJar = plugin.path + "live-plugin.jar"
        val pluginZip = plugin.path + "${plugin.id}.zip"

        if (!pluginXml.exists()) project.createPluginXml(plugin, pluginXml)
        runWriteAction { FileDocumentManager.getInstance().saveAllDocuments() }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Packaging ${plugin.id}", false, ALWAYS_BACKGROUND) {
            override fun run(indicator: ProgressIndicator) {
                if (!livePluginJar.exists()) error("Couldn't find '${livePluginJar.value}'")

                val compilerOutput = livePluginsCompiledPath + "${plugin.id}-${plugin.path.value.hashCode()}"
                if (SrcHashCode(plugin.path, compilerOutput).needsUpdate()) {
                    mainKotlinPluginRunner.setup(plugin, project)
                }

                Compressor.Jar(pluginJar.toFile()).use { jar ->
                    jar.addManifest(Manifest(ByteArrayInputStream("Manifest-Version: 1.0\n".toByteArray())))
                    jar.addFile("META-INF/plugin.xml", pluginXml.toFile())
                    compilerOutput.allFiles()
                        .filterNot { it.name == hashFileName }
                        .forEach { filePath ->
                            val relativePath = filePath.value.removePrefix(compilerOutput.value + "/")
                            jar.addFile(relativePath, filePath.toFile())
                        }
                    plugin.path.allFiles()
                        .filterNot { it == pluginXml || it == pluginJar || it == pluginZip }
                        .forEach { filePath ->
                            val relativePath = filePath.value.removePrefix(plugin.path.value + "/")
                            jar.addFile(relativePath, filePath.toFile())
                        }
                }

                Compressor.Jar(livePluginTrimmedJar.toFile()).use { jar ->
                    JBZipFile(livePluginJar.toFile()).entries.asSequence()
                        .filter { it.name.startsWith("liveplugin") && (it.isDirectory || it.name.endsWith(".class")) }
                        .filterNot { it.name.startsWith("liveplugin/toolwindow") || it.name.startsWith("liveplugin/testrunner") }
                        .forEach {
                            if (it.isDirectory) jar.addDirectory(it.name)
                            else jar.addFile(it.name, it.data)
                        }
                }

                Compressor.Zip(pluginZip.toFile()).use { zip ->
                    val libDir = plugin.id + "/lib"
                    zip.addDirectory(libDir)
                    zip.addFile("$libDir/${pluginJar.name}", pluginJar.toFile())
                    zip.addFile("$libDir/${livePluginTrimmedJar.name}", livePluginTrimmedJar.toFile())
                }

                livePluginTrimmedJar.delete()
                pluginJar.delete()

                @Suppress("DEPRECATION") // There is no non-deprecated alternative to URL_OPENING_LISTENER
                livePluginNotificationGroup.createNotification(
                    title = "Packaged plugin into ${pluginZip.name}",
                    content = "You can upload it to <a href=\"https://plugins.jetbrains.com\">Plugins Marketplace</a> " +
                        "or share as a file and install with <b>Install Plugin from Disk</b> action.",
                    type = INFORMATION
                ).setListener(URL_OPENING_LISTENER).notify(project)
            }
        })
    }

    private fun Project.createPluginXml(plugin: LivePlugin, filePath: FilePath) {
        val fileContent = readSampleScriptFile("$kotlinExamplesPath/plugin.xml")
            .replaceFirst("com.my.company.unique.plugin.id", plugin.id)
            .replaceFirst(
                "TODO Plugin Name",
                plugin.id.replace('-', ' ').split(' ').filter { it.isNotEmpty() }
                    .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.getDefault()) } }
            )
            .replaceFirst("Your name", System.getProperty("user.name"))
        NewPluginXmlScript(fileContent).createNewFile(this, plugin.path.toVirtualFile() ?: error("Can't create virtual file for '${plugin.path.value}'"))
        val message = "Please review and <a href=\"\">edit its content</a> before publishing the plugin."
        livePluginNotificationGroup.createNotification("Created plugin.xml", message, INFORMATION)
            .addAction(object : NotificationAction("Edit plugin.xml") {
                override fun actionPerformed(event: AnActionEvent, notification: Notification) {
                    val virtualFile = filePath.toVirtualFile() ?: return
                    FileEditorManager.getInstance(this@createPluginXml).openFile(virtualFile, true, true)
                }
            })
            .notify(this)
    }
}
