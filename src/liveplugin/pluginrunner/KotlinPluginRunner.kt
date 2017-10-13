package liveplugin.pluginrunner

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.Function
import com.intellij.util.lang.UrlClassLoader
import liveplugin.LivePluginAppComponent.LIVEPLUGIN_LIBS_PATH
import liveplugin.MyFileUtil.*
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.*
import liveplugin.pluginrunner.kotlin.ideJdkClassesRoots
import liveplugin.pluginrunner.kotlin.ideLibFolder
import liveplugin.pluginrunner.kotlin.jarFilesOf
import org.jetbrains.kotlin.codegen.CompilationException
import java.io.File
import java.io.IOException
import java.net.URL

class KotlinPluginRunner(private val errorReporter: ErrorReporter, private val environment: Map<String, String>): PluginRunner {

    override fun scriptName(): String = MAIN_SCRIPT

    override fun canRunPlugin(pathToPluginFolder: String): Boolean =
        findScriptFileIn(pathToPluginFolder, MAIN_SCRIPT) != null

    private val ideLibsClassLoader by lazy {
        val path = PathManager.getPluginsPath()
        UrlClassLoader.build()
            .urls(
                ideJdkClassesRoots().map { URL("file:///${it.absolutePath}") } +
                listFilesIn(ideLibFolder()).map { URL("file:///${it.absolutePath}") } +
                listOf(
                    URL("file:///$path/LivePlugin/lib/__/kotlin-compiler-embeddable.jar"),
                    URL("file:///$path/LivePlugin/lib/__/kotlin-reflect.jar"),
                    URL("file:///$path/LivePlugin/lib/__/kotlin-stdlib.jar"),
                    URL("file:///$path/LivePlugin/lib/__/kotlin-stdlib.jar"),
                    URL("file:///$path/LivePlugin/lib/LivePlugin.jar")
                )
            )
            .useCache()
            .get()
    }

    override fun runPlugin(pathToPluginFolder: String, pluginId: String, binding: Map<String, *>, runOnEDTCallback: Function<Runnable, Void>) {
        val kotlinAddToClasspathKeyword = "// " + PluginRunner.ADD_TO_CLASSPATH_KEYWORD
        val kotlinDependsOnPluginKeyword = "// " + PluginRunner.DEPENDS_ON_PLUGIN_KEYWORD

        val mainScriptUrl = asUrl(findScriptFileIn(pathToPluginFolder, MAIN_SCRIPT))
        val dependentPlugins = findPluginDependencies(readLines(mainScriptUrl), kotlinDependsOnPluginKeyword)
        val compilerOutputPath = FileUtilRt.toSystemIndependentName("${PathManager.getPluginsPath()}/$pluginId/live-plugins-classes")

        val runtimeClassPath = ArrayList<String>().apply {
            addAll(findClasspathAdditions(readLines(mainScriptUrl), kotlinAddToClasspathKeyword, environment + Pair("PLUGIN_PATH", pathToPluginFolder), { path ->
                errorReporter.addLoadingError(pluginId, "Couldn't find dependency '$path'")
                null
            }))
            addAll(jarFilesOf(dependentPlugins).map { it.absolutePath })
            addAll(listFilesIn(File(LIVEPLUGIN_LIBS_PATH)).map { it.absolutePath })
            add(compilerOutputPath)
        }

        val compilerClasspath = ArrayList<String>().apply {
            addAll(ideJdkClassesRoots().map { it.absolutePath })
            addAll(listFilesIn(ideLibFolder()).map { it.absolutePath })
            addAll(listFilesIn(File(LIVEPLUGIN_LIBS_PATH + "/__/")).map { it.absolutePath })
            addAll(runtimeClassPath)
            add(pathToPluginFolder)
        }

        try {
            val classLoader = UrlClassLoader.build()
                .urls(compilerClasspath.map{ URL("file:///$it") }) // prefix with "file:///" so that unix-like paths work on windows
                .parent(ideLibsClassLoader)
                .useCache()
                .get()
            val aClass = classLoader.loadClass("liveplugin.pluginrunner.kotlin.EmbeddedCompilerRunnerKt")

            for (method in aClass.declaredMethods) {
                if (method.name == "compilePlugin") {
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val compilationErrors = method.invoke(
                            null,
                            pathToPluginFolder,
                            compilerClasspath,
                            compilerOutputPath
                        ) as List<String>

                        if (compilationErrors.isNotEmpty()) {
                            errorReporter.addLoadingError(pluginId, compilationErrors.joinToString("\n"))
                            return
                        }
                    } catch (e: IOException) {
                        errorReporter.addLoadingError(pluginId, "Error creating scripting engine. " + e.message)
                    } catch (e: CompilationException) {
                        errorReporter.addLoadingError(pluginId, "Error compiling script. " + e.message)
                    } catch (e: Throwable) {
                        errorReporter.addLoadingError(pluginId, "Internal error compiling script. " + e.message)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace() // TODO
            throw e
        }

        try {
            val classLoader = createClassLoaderWithDependencies(runtimeClassPath, dependentPlugins, mainScriptUrl, pluginId, errorReporter)
            val aClass = classLoader.loadClass("Plugin")

            runOnEDTCallback.`fun`(Runnable {
                try {
                    // Arguments below must match constructor of liveplugin.pluginrunner.KotlinScriptTemplate class.
                    // There doesn't seem to be a way to add binding as Map, therefore, hardcoding them.
                    aClass.constructors[0].newInstance(
                        binding["project"] as Project,
                        binding["isIdeStartup"] as Boolean,
                        binding["pluginPath"] as String,
                        binding["pluginDisposable"] as Disposable
                    )
                } catch (e: Throwable) {
                    errorReporter.addRunningError(pluginId, e)
                }
            })

        } catch (e: Exception) {
            e.printStackTrace() // TODO
            throw e
        }
    }

    companion object {
        @JvmField val MAIN_SCRIPT = "plugin.kts"
    }
}
