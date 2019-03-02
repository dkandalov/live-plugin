package liveplugin.pluginrunner.kotlin

import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt.toSystemIndependentName
import com.intellij.util.lang.UrlClassLoader
import liveplugin.IdeUtil.unscrambleThrowable
import liveplugin.LivePluginAppComponent.Companion.livePluginCompilerLibsPath
import liveplugin.LivePluginAppComponent.Companion.livePluginLibsPath
import liveplugin.LivePluginAppComponent.Companion.livePluginsClassesPath
import liveplugin.MyFileUtil.filesList
import liveplugin.MyFileUtil.findScriptFileIn
import liveplugin.MyFileUtil.toUrl
import liveplugin.pluginrunner.*
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
            ideLibFiles() +
            File(livePluginLibsPath).filesList() +
            File(livePluginCompilerLibsPath).filesList()
        ).map { it.toUrl() })
        .noPreload()
        .allowBootstrapResources()
        .useCache()
        .get()
}

/**
 * There are several reasons to run kotlin plugins the way it's currently done.
 *
 * Use standalone compiler (i.e. `kotlin-compiler-embeddable.jar`, etc.) because
 *  - kotlin script seems to have few problems and using normal compiler should be easier
 *  - each version of liveplugin will have the same version of kotlin compiler
 *  - size of LivePlugin.zip shouldn't be an issue
 *
 * Use separate classloader to compile liveplugin and then load compiler classes into IDE because
 *  - kotlin compiler uses classes with the same names as IntelliJ
 *  - `kotlin-compiler-embeddable` has some of them renamed but not all of them
 *  - kotlin compiler attempts to initialise some of the global variables which are already initialised by IDE
 *  - in theory kotlin-compiler and IDE classes could be "namespaced" by classloader,
 *    however in practice it still causes confusing problems which are really hard to debug
 *    ^^^ don't understand what it means anymore :(
 *
 * Use ".kts" extension because
 *  - ".kt" must have "main" function to be executed
 *  - `LivePluginKotlinScriptDefinitionContributor` doesn't seem to work with ".kt" files.
 */
class KotlinPluginRunner(private val errorReporter: ErrorReporter, private val environment: Map<String, String>): PluginRunner {

    override fun scriptName(): String = mainScript

    override fun runPlugin(pluginFolderPath: String, pluginId: String, binding: Map<String, *>, runOnEDT: (() -> Unit) -> Unit) {
        val kotlinAddToClasspathKeyword = "// " + PluginRunner.addToClasspathKeyword
        val kotlinDependsOnPluginKeyword = "// " + PluginRunner.dependsOnPluginKeyword

        val pluginFolder = File(pluginFolderPath)
        val mainScriptFile = findScriptFileIn(pluginFolderPath, mainScript)!!
        val dependentPlugins = findPluginDependencies(mainScriptFile.readLines(), kotlinDependsOnPluginKeyword)
        val compilerOutput = File(toSystemIndependentName("$livePluginsClassesPath/$pluginId"))
        compilerOutput.deleteRecursively()

        val scriptPathAdditions = findClasspathAdditions(mainScriptFile.readLines(), kotlinAddToClasspathKeyword, environment + Pair("PLUGIN_PATH", pluginFolderPath), onError = { path ->
            errorReporter.addLoadingError(pluginId, "Couldn't find dependency '$path'")
        }).map{ File(it) }

        val compilerClasspath =
            ideJdkClassesRoots() +
            ideLibFiles() +
            File(livePluginLibsPath).filesList() +
            File(livePluginCompilerLibsPath).filesList() +
            jarFilesOf(dependentPlugins) +
            scriptPathAdditions +
            pluginFolder

        val compilerClassLoader = UrlClassLoader.build()
            .urls((jarFilesOf(dependentPlugins) + scriptPathAdditions + pluginFolder).map { it.toUrl() })
            .parent(ideLibsClassLoader)
            .useCache()
            .get()
        val compilerRunner = compilerClassLoader.loadClass("liveplugin.pluginrunner.kotlin.compiler.EmbeddedCompilerRunnerKt")

        compilerRunner.declaredMethods.find { it.name == "compilePlugin" }!!.let { method ->
            try {
                @Suppress("UNCHECKED_CAST")
                val compilationErrors = method.invoke(null, pluginFolderPath, compilerClasspath, compilerOutput) as List<String>
                if (compilationErrors.isNotEmpty()) {
                    errorReporter.addLoadingError(pluginId, "Error compiling script. " + compilationErrors.joinToString("\n"))
                    return
                }
            } catch (e: IOException) {
                errorReporter.addLoadingError(pluginId, "Error creating scripting engine. ${unscrambleThrowable(e)}")
            } catch (e: CompilationException) {
                errorReporter.addLoadingError(pluginId, "Error compiling script. ${unscrambleThrowable(e)}")
            } catch (e: Throwable) {
                errorReporter.addLoadingError(pluginId, "Internal error compiling script. ${unscrambleThrowable(e)}")
            }
        }

        val pluginClass = try {
            val runtimeClassPath =
                listOf(compilerOutput) +
                    File(livePluginLibsPath).filesList() +
                scriptPathAdditions

            val classLoader = createClassLoaderWithDependencies(
                runtimeClassPath,
                dependentPlugins,
                pluginId,
                errorReporter
            )
            classLoader.loadClass("Plugin")
        } catch (e: Throwable) {
            errorReporter.addLoadingError(pluginId, "Error while loading plugin class. ${unscrambleThrowable(e)}")
            return
        }

        runOnEDT {
            try {
                // Arguments below must match constructor of liveplugin.pluginrunner.kotlin.KotlinScriptTemplate class.
                // There doesn't seem to be a way to add binding as Map, therefore, hardcoding them.
                pluginClass.constructors[0].newInstance(
                    binding[projectKey] as Project?,
                    binding[isIdeStartupKey] as Boolean,
                    binding[pluginPathKey] as String,
                    binding[pluginDisposableKey] as Disposable
                )
            } catch (e: Throwable) {
                errorReporter.addRunningError(pluginId, e)
            }
        }
    }

    companion object {
        const val mainScript = "plugin.kts"
        const val testScript = "plugin-test.kts"
        const val kotlinAddToClasspathKeyword = "// " + PluginRunner.addToClasspathKeyword
        const val kotlinDependsOnPluginKeyword = "// " + PluginRunner.dependsOnPluginKeyword
    }
}

private fun ideJdkClassesRoots(): List<File> =
    JavaSdkUtil.getJdkClassesRoots(File(System.getProperty("java.home")), true)

private fun ideLibFiles(): List<File> {
    val ideJarPath = PathManager.getJarPathForClass(IntelliJLaf::class.java) ?: throw IllegalStateException("Failed to find IDE lib folder.")
    return File(ideJarPath).parentFile.filesList()
}

private fun jarFilesOf(dependentPlugins: List<String>): List<File> {
    val pluginDescriptors = pluginDescriptorsOf(dependentPlugins, onError = { it -> throw IllegalStateException("Failed to find jar for dependent plugin '$it'.") })
    return pluginDescriptors.map { it -> it.path }
}