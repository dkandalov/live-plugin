package liveplugin.pluginrunner.kotlin

import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.lang.UrlClassLoader
import liveplugin.LivePluginAppComponent.livepluginCompilerLibsPath
import liveplugin.LivePluginAppComponent.livepluginLibsPath
import liveplugin.MyFileUtil.findScriptFileIn
import liveplugin.MyFileUtil.listFilesIn
import liveplugin.pluginrunner.ErrorReporter
import liveplugin.pluginrunner.PluginRunner
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.createClassLoaderWithDependencies
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.findClasspathAdditions
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.findPluginDependencies
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.pluginDescriptorsOf
import org.jetbrains.jps.model.java.impl.JavaSdkUtil
import org.jetbrains.kotlin.codegen.CompilationException
import java.io.File
import java.io.IOException

private val ideLibsClassLoader by lazy {
    UrlClassLoader.build()
        .urls((ideJdkClassesRoots() +
                listFilesIn(ideLibFolder()) +
                listFilesIn(File(livepluginLibsPath)) +
                listFilesIn(File(livepluginCompilerLibsPath))
        ).map { it.toUrl() })
        .useCache()
        .get()
}

class KotlinPluginRunner(private val errorReporter: ErrorReporter, private val environment: Map<String, String>): PluginRunner {

    override fun scriptName(): String = mainScript

    override fun canRunPlugin(pathToPluginFolder: String): Boolean =
        findScriptFileIn(pathToPluginFolder, mainScript) != null

    override fun runPlugin(pathToPluginFolder: String, pluginId: String, binding: Map<String, *>, runOnEDT: (() -> Unit) -> Unit) {
        val kotlinAddToClasspathKeyword = "// " + PluginRunner.addToClasspathKeyword
        val kotlinDependsOnPluginKeyword = "// " + PluginRunner.dependsOnPluginKeyword

        val pluginFolder = File(pathToPluginFolder)
        val mainScriptFile = findScriptFileIn(pathToPluginFolder, mainScript)!!
        val dependentPlugins = findPluginDependencies(mainScriptFile.readLines().toTypedArray(), kotlinDependsOnPluginKeyword)
        val compilerOutput = File(FileUtilRt.toSystemIndependentName("${PathManager.getPluginsPath()}/live-plugins-classes/$pluginId"))
        compilerOutput.deleteRecursively()

        val scriptPathAdditions = findClasspathAdditions(mainScriptFile.readLines().toTypedArray(), kotlinAddToClasspathKeyword, environment + Pair("PLUGIN_PATH", pathToPluginFolder), onError = { path ->
            errorReporter.addLoadingError(pluginId, "Couldn't find dependency '$path'")
        }).map{ File(it) }

        val compilerClasspath = ArrayList<File>().apply {
            addAll(ideJdkClassesRoots())
            addAll(listFilesIn(ideLibFolder()))
            addAll(listFilesIn(File(livepluginLibsPath)))
            addAll(listFilesIn(File(livepluginCompilerLibsPath)))
            addAll(jarFilesOf(dependentPlugins))
            addAll(scriptPathAdditions)
            add(pluginFolder)
        }
        val compilerClassLoader = UrlClassLoader.build()
            .urls((jarFilesOf(dependentPlugins) + scriptPathAdditions + pluginFolder).map { it.toUrl() })
            .parent(ideLibsClassLoader)
            .useCache()
            .get()
        val compilerRunner = compilerClassLoader.loadClass("liveplugin.pluginrunner.kotlin.compiler.EmbeddedCompilerRunnerKt")

        compilerRunner.declaredMethods.find { it.name == "compilePlugin" }!!.let { method ->
            try {
                @Suppress("UNCHECKED_CAST")
                val compilationErrors = method.invoke(null, pathToPluginFolder, compilerClasspath, compilerOutput) as List<String>
                if (compilationErrors.isNotEmpty()) {
                    errorReporter.addLoadingError(pluginId, compilationErrors.joinToString("\n"))
                    return
                }
            } catch (e: IOException) {
                errorReporter.addLoadingError(pluginId, "Error creating scripting engine. ${e.message}")
            } catch (e: CompilationException) {
                errorReporter.addLoadingError(pluginId, "Error compiling script. ${e.message}")
            } catch (e: Throwable) {
                errorReporter.addLoadingError(pluginId, "Internal error compiling script. ${e.message}")
            }
        }

        val pluginClass = try {
            val runtimeClassPath = ArrayList<File>().apply {
                add(compilerOutput)
                addAll(listFilesIn(File(livepluginLibsPath)))
                addAll(jarFilesOf(dependentPlugins))
                addAll(scriptPathAdditions)
            }
            val classLoader = createClassLoaderWithDependencies(
                runtimeClassPath.map{ it.absolutePath },
                dependentPlugins,
                mainScriptFile.toUrl().toString(),
                pluginId,
                errorReporter
            )
            classLoader.loadClass("Plugin")
        } catch (e: Exception) {
            errorReporter.addLoadingError(pluginId, "Error while loading plugin class. ${e.message}")
            return
        }

        runOnEDT {
            try {
                // Arguments below must match constructor of liveplugin.pluginrunner.kotlin.KotlinScriptTemplate class.
                // There doesn't seem to be a way to add binding as Map, therefore, hardcoding them.
                pluginClass.constructors[0].newInstance(
                    binding["project"] as Project,
                    binding["isIdeStartup"] as Boolean,
                    binding["pluginPath"] as String,
                    binding["pluginDisposable"] as Disposable
                )
            } catch (e: Throwable) {
                errorReporter.addRunningError(pluginId, e)
            }
        }
    }

    companion object {
        @JvmField val mainScript = "plugin.kts"
    }
}

private fun File.toUrl() = this.toURI().toURL()

private fun ideJdkClassesRoots(): List<File> =
    JavaSdkUtil.getJdkClassesRoots(File(System.getProperty("java.home")), true)

private fun ideLibFolder(): File {
    val ideJarPath = PathManager.getJarPathForClass(IntelliJLaf::class.java) ?: throw IllegalStateException("Failed to find IDE lib folder.")
    return File(ideJarPath).parentFile
}

private fun jarFilesOf(dependentPlugins: List<String>): List<File> {
    val pluginDescriptors = pluginDescriptorsOf(dependentPlugins, onError = { it -> throw IllegalStateException("Failed to find jar for dependent plugin '$it'.") })
    return pluginDescriptors.map { it -> it.path }
}