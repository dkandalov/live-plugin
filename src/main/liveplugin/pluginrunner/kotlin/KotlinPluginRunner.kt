package liveplugin.pluginrunner.kotlin

import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt.toSystemIndependentName
import com.intellij.util.lang.UrlClassLoader
import liveplugin.IdeUtil.unscrambleThrowable
import liveplugin.LivePluginPaths
import liveplugin.LivePluginPaths.livePluginLibPath
import liveplugin.filesList
import liveplugin.findScriptFileIn
import liveplugin.pluginrunner.*
import liveplugin.pluginrunner.AnError.*
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.createClassLoaderWithDependencies
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.findClasspathAdditions
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.findPluginDependencies
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.pluginDescriptorsOf
import liveplugin.pluginrunner.Result.*
import liveplugin.toUrl
import org.jetbrains.jps.model.java.impl.JavaSdkUtil
import java.io.File
import java.io.IOException

/**
 * There are several reasons to run kotlin plugins the way it's currently done.
 *
 * Use standalone compiler (i.e. `kotlin-compiler-embeddable.jar`, etc.) because
 *  - kotlin script seems to have few problems, so using normal compiler should be more stable
 *  - each version of liveplugin will have the same version of kotlin compiler
 *  - size of LivePlugin.zip shouldn't be an issue
 *
 * Use separate classloader to compile liveplugin and then load compiler classes into IDE because
 *  - kotlin compiler uses classes with the same names as IntelliJ
 *  - `kotlin-compiler-embeddable` has some of the classes with duplicate names renamed but not all of them
 *  - kotlin compiler attempts to initialise some of the global variables which are already initialised by IDE
 *  - in theory kotlin-compiler and IDE classes could be "namespaced" by classloader,
 *    however, in practice it still causes confusing problems which are really hard to debug
 *
 * Use ".kts" extension because
 *  - ".kt" must have "main" function to be executed
 *  - ".kt" won't work with `LivePluginScriptCompilationConfiguration`
 */
class KotlinPluginRunner(
    override val scriptName: String,
    private val systemEnvironment: Map<String, String> = systemEnvironment()
): PluginRunner {

    override fun runPlugin(pluginFolderPath: String, pluginId: String, binding: Map<String, *>, runOnEDT: (() -> Result<Unit, AnError>) -> Result<Unit, AnError>): Result<Unit, AnError> {
        val mainScriptFile = findScriptFileIn(pluginFolderPath, mainScript)!!
        val dependentPlugins = findPluginDependencies(mainScriptFile.readLines(), kotlinDependsOnPluginKeyword)
        val environment = systemEnvironment + Pair("PLUGIN_PATH", pluginFolderPath)
        val scriptPathAdditions = findClasspathAdditions(mainScriptFile.readLines(), kotlinAddToClasspathKeyword, environment)
            .onFailure { path -> return Failure(LoadingError(pluginId, "Couldn't find dependency '$path'")) }

        val compilerOutput = File(toSystemIndependentName("${LivePluginPaths.livePluginsCompiledPath}/$pluginId")).also { it.deleteRecursively() }

        val compilerRunnerClass = compilerClassLoader.loadClass("liveplugin.pluginrunner.kotlin.compiler.EmbeddedCompilerRunnerKt")
        compilerRunnerClass.declaredMethods.find { it.name == "compile" }!!.let { compilePluginMethod ->
            try {
                val compilerClasspath =
                    ideJdkClassesRoots() +
                    ideLibFiles() +
                    File(livePluginLibPath).filesList() +
                    File(livePluginCompilerLibsPath).filesList() +
                    jarFilesOf(dependentPlugins) +
                    scriptPathAdditions +
                    File(pluginFolderPath)

                @Suppress("UNCHECKED_CAST")
                val compilationErrors = compilePluginMethod.invoke(null, pluginFolderPath, compilerClasspath, compilerOutput, KotlinScriptTemplate::class.java) as List<String>
                if (compilationErrors.isNotEmpty()) {
                    return Failure(LoadingError(pluginId, "Error compiling script. " + compilationErrors.joinToString("\n")))
                }
            } catch (e: IOException) {
                return Failure(LoadingError(pluginId, "Error creating scripting engine. ${unscrambleThrowable(e)}"))
            } catch (e: Throwable) {
                // Don't depend directly on `CompilationException` because it's part of Kotlin plugin
                // and LivePlugin should be able to run kotlin scripts without it
                val error = if (e.javaClass.canonicalName == "org.jetbrains.kotlin.codegen.CompilationException") {
                    LoadingError(pluginId, "Error compiling script. ${unscrambleThrowable(e)}")
                } else {
                    LoadingError(pluginId, "Internal error compiling script. ${unscrambleThrowable(e)}")
                }
                return Failure(error)
            }
        }

        val pluginClass = try {
            val runtimeClassPath =
                listOf(compilerOutput) +
                File(livePluginLibPath).filesList() +
                scriptPathAdditions

            val classLoader = createClassLoaderWithDependencies(runtimeClassPath, dependentPlugins, pluginId).onFailure {
                return Failure(LoadingError(it.reason.pluginId, it.reason.message))
            }
            classLoader.loadClass("Plugin")
        } catch (e: Throwable) {
            return Failure(LoadingError(pluginId, "Error while loading plugin class. ${unscrambleThrowable(e)}"))
        }

        return runOnEDT {
            try {
                // Arguments below must match constructor of KotlinScriptTemplate class.
                // There doesn't seem to be a way to add binding as Map, therefore, hardcoding them.
                pluginClass.constructors[0].newInstance(
                    binding[isIdeStartupKey] as Boolean,
                    binding[projectKey] as Project?,
                    binding[pluginPathKey] as String,
                    binding[pluginDisposableKey] as Disposable
                )
                Success(Unit)
            } catch (e: Throwable) {
                Failure(RunningError(pluginId, e))
            }
        }
    }

    companion object {
        const val mainScript = "plugin.kts"
        const val testScript = "plugin-test.kts"
        const val kotlinAddToClasspathKeyword = "// " + PluginRunner.addToClasspathKeyword
        const val kotlinDependsOnPluginKeyword = "// " + PluginRunner.dependsOnPluginKeyword

        val main = KotlinPluginRunner(mainScript)
        val test = KotlinPluginRunner(testScript)

        private val livePluginCompilerLibsPath = toSystemIndependentName("${LivePluginPaths.livePluginPath}/kotlin-compiler")

        private val compilerClassLoader by lazy {
            UrlClassLoader.build()
                .urls((ideJdkClassesRoots() + File(livePluginCompilerLibsPath).filesList()).map(File::toUrl))
                .noPreload()
                .allowBootstrapResources()
                .useCache()
                .get()
        }
    }
}

private fun ideJdkClassesRoots(): List<File> = JavaSdkUtil.getJdkClassesRoots(javaHome, true)

val javaHome = File(System.getProperty("java.home"))

private fun ideLibFiles(): List<File> {
    val ideJarPath = PathManager.getJarPathForClass(IntelliJLaf::class.java)
        ?: error("Failed to find IDE lib folder.")
    return File(ideJarPath).parentFile.filesList()
}

private fun jarFilesOf(dependentPlugins: List<String>): List<File> {
    return pluginDescriptorsOf(dependentPlugins)
        .onFailure { error("Failed to find jar for dependent plugin '$it'.") }
        .map { it.path }
}