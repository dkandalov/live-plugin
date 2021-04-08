package liveplugin.toolwindow

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages.CANCEL
import com.intellij.openapi.ui.Messages.showOkCancelDialog
import com.intellij.openapi.util.NlsContexts.*
import com.intellij.util.io.Compressor
import liveplugin.Icons
import liveplugin.LivePluginPaths
import liveplugin.pluginrunner.LivePlugin
import liveplugin.pluginrunner.canBeHandledBy
import liveplugin.pluginrunner.kotlin.KotlinPluginRunner
import liveplugin.pluginrunner.kotlin.SrcHashCode
import liveplugin.pluginrunner.kotlin.SrcHashCode.Companion.hashFileName
import liveplugin.pluginrunner.selectedFilePaths
import liveplugin.pluginrunner.toLivePlugins
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
        event.selectedFilePaths().toLivePlugins()
            .forEach { packagePlugin(it, project, event) }
    }

    @Suppress("UnstableApiUsage")
    private fun Project.showOkCancelDialog(
        @DialogMessage message: String,
        @DialogTitle title: String,
        @Button okText: String,
        @Button cancelText: String
    ): Unit? = if (showOkCancelDialog(this, message, title, okText, cancelText, null) == CANCEL) null else Unit

    private fun packagePlugin(plugin: LivePlugin, project: Project, event: AnActionEvent) {
        val pluginXml = plugin.path + "plugin.xml"
        if (!pluginXml.exists()) {
            project.showOkCancelDialog("Do you want to create plugin.xml?", "", "Ok", "Cancel") ?: return 
            NewPluginXmlScript().actionPerformed(event) // TODO replace plugin name with plugin.id
            // TODO FileEditorManager.getInstance(project).openFile(virtualFile, true)
        }

        val compilerOutput = LivePluginPaths.livePluginsCompiledPath + plugin.id
        val livePluginJar = LivePluginPaths.livePluginLibPath + "LivePlugin.jar"

        val jarFile = (plugin.path + "plugin.jar").toFile()
        val zipFile = (plugin.path + "plugin.zip").toFile()
        if (zipFile.exists()) {
            project.showOkCancelDialog(
                "File ${zipFile.name} already exists. Do you want to continue and overwrite it?",
                "",
                "Ok",
                "Cancel"
            ) ?: return
        }
        // TODO ask "do you want to overwrite" jar/zip?

        // TODO run the code below in background
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
                    // TODO check adding nested dirs with files
                    val relativePath = filePath.value.removePrefix(compilerOutput.value + "/")
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
}
