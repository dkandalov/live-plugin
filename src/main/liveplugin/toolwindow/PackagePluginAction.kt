package liveplugin.toolwindow

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
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
import java.util.jar.Manifest

class PackagePluginAction: AnAction(
    "Package Plugin",
    "Package selected plugins so they can be uploaded to plugins marketplace",
    Icons.packagePluginIcon
), DumbAware {
    override fun actionPerformed(event: AnActionEvent) {
        event.selectedFilePaths().toLivePlugins().forEach { packagePlugin(it) }
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.selectedFilePaths().canBeHandledBy(listOf(KotlinPluginRunner.main))
    }

    private fun packagePlugin(plugin: LivePlugin) {
        val pluginXml = plugin.path + "plugin.xml"
        if (!pluginXml.exists()) {
            error("Missing plugin.xml")
        }

        val compilerOutput = LivePluginPaths.livePluginsCompiledPath + plugin.id
        if (SrcHashCode(plugin.path, compilerOutput).needsUpdate()) {
            // TODO do this in background
            KotlinPluginRunner.main.setup(plugin)
        }

        val livePluginJar = LivePluginPaths.livePluginLibPath + "LivePlugin.jar"
        if (!livePluginJar.exists()) error("Couldn't find '${livePluginJar.value}'")

        val jarFile = (plugin.path + "${plugin.id}.jar").toFile()
        Compressor.Jar(jarFile).use { jar ->
            jar.addManifest(Manifest())
            jar.addFile("META-INF/plugin.xml", pluginXml.toFile())
            compilerOutput.allFiles()
                .filterNot { it.name == hashFileName }
                .forEach { filePath ->
                    // TODO check adding nested dirs with files
                    val relativePath = filePath.value.removePrefix(compilerOutput.value)
                    jar.addFile(relativePath, filePath.toFile())
                }
        }

        Compressor.Zip((plugin.path + "${plugin.id}.zip").toFile()).use { zip ->
            val libDir = plugin.id + "/lib"
            zip.addDirectory(libDir)
            zip.addFile("$libDir/plugin.jar", jarFile)
            zip.addFile("$libDir/LivePlugin.jar", livePluginJar.toFile())
        }

        jarFile.delete()
    }
}
