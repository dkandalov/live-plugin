package liveplugin.toolwindow

import com.intellij.ide.BrowserUtil.browse
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
import com.intellij.util.io.Compressor
import com.intellij.util.io.zip.JBZipFile
import liveplugin.FilePath
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

class CreatePluginZipAction: AnAction(
    "Create Plugin Zip",
    "Package selected live plugin into zip so that it can be uploaded to plugins marketplace",
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
        val pluginJarFile = (plugin.path + "${plugin.id}.jar").toFile()
        val livePluginJar = LivePluginPaths.livePluginLibPath + "LivePlugin.jar"
        val livePluginTrimmedJar = plugin.path + "LivePlugin.jar"
        val zipFile = (plugin.path + "${plugin.id}.zip").toFile()

        if (!pluginXml.exists()) project.createPluginXml(plugin, pluginXml)
        if (pluginJarFile.exists()) {
            val message = "File ${pluginJarFile.name} already exists. Do you want to continue and overwrite it?"
            if (showOkCancelDialog(project, message, "Package Plugin", "Ok", "Cancel", null) == CANCEL) return
        }

        ProgressManager.getInstance().run(object: Task.Backgroundable(project, "Packaging ${plugin.id}", false, ALWAYS_BACKGROUND) {
            override fun run(indicator: ProgressIndicator) {
                if (!livePluginJar.exists()) error("Couldn't find '${livePluginJar.value}'")

                val compilerOutput = LivePluginPaths.livePluginsCompiledPath + plugin.id
                if (SrcHashCode(plugin.path, compilerOutput).needsUpdate()) {
                    KotlinPluginRunner.main.setup(plugin)
                }

                Compressor.Jar(pluginJarFile).use { jar ->
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
                        .filterNot { it == pluginXml || it == pluginJarFile.toFilePath() || it == zipFile.toFilePath() }
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

                Compressor.Zip(zipFile).use { zip ->
                    val libDir = plugin.id + "/lib"
                    zip.addDirectory(libDir)
                    zip.addFile("$libDir/${pluginJarFile.name}", pluginJarFile)
                    zip.addFile("$libDir/${livePluginTrimmedJar.name}", livePluginTrimmedJar.toFile())
                }

                livePluginTrimmedJar.toFile().delete()
                pluginJarFile.delete()

                val message = "You can now upload it to <a href=\"https://plugins.jetbrains.com\">Plugins Marketplace</a> " +
                    "or share as a file and install with <b>Install Plugin from Disk</b> action."
                livePluginNotificationGroup.createNotification("Packaged plugin into ${zipFile.name}", message, INFORMATION)
                    .setListener { _, event -> browse(event.url) }
                    .notify(project)
            }
        })
    }

    private fun Project.createPluginXml(plugin: LivePlugin, filePath: FilePath) {
        val fileContent = readSampleScriptFile("${LivePluginPaths.kotlinExamplesPath}/plugin.xml")
            .replaceFirst("com.your.company.unique.plugin.id", plugin.id)
            .replaceFirst("TODO Plugin Name", plugin.id.replace('-', ' ').split(' ').filter { it.isNotEmpty() }.joinToString(" ") { it.capitalize() })
            .replaceFirst("Your name", System.getProperty("user.name"))
        NewPluginXmlScript(fileContent).createNewFile(this, plugin.path.toVirtualFile() ?: error("Can't create virtual file for '${plugin.path.value}'"))
        val message = "Please review and <a href=\"\">edit its content</a> before publishing the plugin."
        livePluginNotificationGroup.createNotification("Created plugin.xml", message, INFORMATION)
            .setListener(NotificationListener { _, _ ->
                val virtualFile = filePath.toVirtualFile() ?: return@NotificationListener
                FileEditorManager.getInstance(this).openFile(virtualFile, true, true)
            })
            .notify(this)
    }
}
