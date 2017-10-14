package liveplugin.pluginrunner

import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.Function
import com.intellij.util.lang.UrlClassLoader
import liveplugin.LivePluginAppComponent.LIVEPLUGIN_COMPILER_LIBS_PATH
import liveplugin.LivePluginAppComponent.LIVEPLUGIN_LIBS_PATH
import liveplugin.MyFileUtil.*
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.*
import org.jetbrains.jps.model.java.impl.JavaSdkUtil
import org.jetbrains.kotlin.codegen.CompilationException
import java.io.File
import java.io.IOException
import java.net.URL

private val ideLibsClassLoader by lazy {
    UrlClassLoader.build()
        .urls((ideJdkClassesRoots() +
                listFilesIn(ideLibFolder()) +
                listFilesIn(File(LIVEPLUGIN_LIBS_PATH)) +
                listFilesIn(File(LIVEPLUGIN_COMPILER_LIBS_PATH))
        ).map { it.toFileUrl() })
        .useCache()
        .get()
}

class KotlinPluginRunner(private val errorReporter: ErrorReporter, private val environment: Map<String, String>): PluginRunner {

    override fun scriptName(): String = MAIN_SCRIPT

    override fun canRunPlugin(pathToPluginFolder: String): Boolean =
        findScriptFileIn(pathToPluginFolder, MAIN_SCRIPT) != null

    override fun runPlugin(pathToPluginFolder: String, pluginId: String, binding: Map<String, *>, runOnEDTCallback: Function<Runnable, Void>) {
        val kotlinAddToClasspathKeyword = "// " + PluginRunner.ADD_TO_CLASSPATH_KEYWORD
        val kotlinDependsOnPluginKeyword = "// " + PluginRunner.DEPENDS_ON_PLUGIN_KEYWORD

        val pluginFolder = File(pathToPluginFolder)
        val mainScriptUrl = asUrl(findScriptFileIn(pathToPluginFolder, MAIN_SCRIPT))
        val dependentPlugins = findPluginDependencies(readLines(mainScriptUrl), kotlinDependsOnPluginKeyword)
        val compilerOutput = File(FileUtilRt.toSystemIndependentName("${PathManager.getPluginsPath()}/live-plugins-classes/$pluginId"))
        compilerOutput.deleteRecursively()

        val scriptPathAdditions = findClasspathAdditions(readLines(mainScriptUrl), kotlinAddToClasspathKeyword, environment + Pair("PLUGIN_PATH", pathToPluginFolder), { path ->
            errorReporter.addLoadingError(pluginId, "Couldn't find dependency '$path'")
            null
        }).map{ File(it) }

        val compilerClasspath = ArrayList<File>().apply {
            addAll(ideJdkClassesRoots())
            addAll(listFilesIn(ideLibFolder()))
            addAll(listFilesIn(File(LIVEPLUGIN_LIBS_PATH)))
            addAll(listFilesIn(File(LIVEPLUGIN_COMPILER_LIBS_PATH)))
            addAll(jarFilesOf(dependentPlugins))
            addAll(scriptPathAdditions)
            add(pluginFolder)
        }
        val compilerClassLoader = UrlClassLoader.build()
            .urls((jarFilesOf(dependentPlugins) + scriptPathAdditions + pluginFolder).map { it.toFileUrl() })
            .parent(ideLibsClassLoader)
            .useCache()
            .get()
        val compilerRunner = compilerClassLoader.loadClass("liveplugin.pluginrunner.kotlin.EmbeddedCompilerRunnerKt")

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
                addAll(listFilesIn(File(LIVEPLUGIN_LIBS_PATH)))
                addAll(jarFilesOf(dependentPlugins))
                addAll(scriptPathAdditions)
            }
            val classLoader = createClassLoaderWithDependencies(runtimeClassPath.map{ it.absolutePath }, dependentPlugins, mainScriptUrl, pluginId, errorReporter)
            classLoader.loadClass("Plugin")
        } catch (e: Exception) {
            errorReporter.addLoadingError(pluginId, "Error while loading plugin class. ${e.message}")
            return
        }

        runOnEDTCallback.`fun`(Runnable {
            try {
                // Arguments below must match constructor of liveplugin.pluginrunner.KotlinScriptTemplate class.
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
        })
    }

    companion object {
        @JvmField val MAIN_SCRIPT = "plugin.kts"
    }
}

private fun File.toFileUrl() = URL("file:///$this") // prefix with "file:///" so that unix-like paths work on windows

private fun ideJdkClassesRoots(): List<File> =
    JavaSdkUtil.getJdkClassesRoots(File(System.getProperty("java.home")), true)

private fun ideLibFolder(): File {
    val ideJarPath = PathManager.getJarPathForClass(IntelliJLaf::class.java) ?: throw IllegalStateException("Failed to find IDE lib folder.")
    return File(ideJarPath).parentFile
}

private fun jarFilesOf(dependentPlugins: List<String>): List<File> {
    val pluginDescriptors = pluginDescriptorsOf(dependentPlugins) { it -> throw IllegalStateException("Failed to find jar for dependent plugin '$it'.") }
    return pluginDescriptors.map { it -> it.path }
}