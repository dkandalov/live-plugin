package liveplugin.pluginrunner.kotlin

import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt.toSystemIndependentName
import com.intellij.util.lang.UrlClassLoader
import liveplugin.IdeUtil.unscrambleThrowable
import liveplugin.LivePluginAppComponent.Companion.livePluginLibsPath
import liveplugin.LivePluginAppComponent.Companion.livePluginPath
import liveplugin.LivePluginAppComponent.Companion.livePluginsCompiledPath
import liveplugin.filesList
import liveplugin.findScriptFileIn
import liveplugin.pluginrunner.*
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.createClassLoaderWithDependencies
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.findClasspathAdditions
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.findPluginDependencies
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.pluginDescriptorsOf
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
class KotlinPluginRunner(private val errorReporter: ErrorReporter, private val environment: Map<String, String>): PluginRunner {

    override fun scriptName(): String = mainScript

    override fun runPlugin(pluginFolderPath: String, pluginId: String, binding: Map<String, *>, runOnEDT: (() -> Unit) -> Unit) {
        val mainScriptFile = findScriptFileIn(pluginFolderPath, mainScript)!!
        val dependentPlugins = findPluginDependencies(mainScriptFile.readLines(), kotlinDependsOnPluginKeyword)
        val scriptPathAdditions = findClasspathAdditionsIn(mainScriptFile.readLines(), pluginFolderPath, pluginId)
        val compilerOutput = File(toSystemIndependentName("$livePluginsCompiledPath/$pluginId")).also { it.deleteRecursively() }

        val compilerRunnerClass = compilerClassLoader.loadClass("liveplugin.pluginrunner.kotlin.compiler.EmbeddedCompilerRunnerKt")
        compilerRunnerClass.declaredMethods.find { it.name == "compile" }!!.let { compilePluginMethod ->
            try {
                val compilerClasspath =
                    ideJdkClassesRoots() +
                    ideLibFiles() +
                    File(livePluginLibsPath).filesList() +
                    File(livePluginCompilerLibsPath).filesList() +
                    jarFilesOf(dependentPlugins) +
                    scriptPathAdditions +
                    File(pluginFolderPath)

                @Suppress("UNCHECKED_CAST")
                val compilationErrors = compilePluginMethod.invoke(null, pluginFolderPath, compilerClasspath, compilerOutput, KotlinScriptTemplate::class.java) as List<String>
                if (compilationErrors.isNotEmpty()) {
                    errorReporter.addLoadingError(pluginId, "Error compiling script. " + compilationErrors.joinToString("\n"))
                    return
                }
            } catch (e: IOException) {
                errorReporter.addLoadingError(pluginId, "Error creating scripting engine. ${unscrambleThrowable(e)}")
                return
            } catch (e: Throwable) {
                // Don't depend directly on `CompilationException` because it's part of Kotlin plugin
                // and LivePlugin should be able to run kotlin scripts without it
                if (e.javaClass.canonicalName == "org.jetbrains.kotlin.codegen.CompilationException") {
                    errorReporter.addLoadingError(pluginId, "Error compiling script. ${unscrambleThrowable(e)}")
                } else {
                    errorReporter.addLoadingError(pluginId, "Internal error compiling script. ${unscrambleThrowable(e)}")
                }
                return
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
                // Arguments below must match constructor of KotlinScriptTemplate class.
                // There doesn't seem to be a way to add binding as Map, therefore, hardcoding them.
                pluginClass.constructors[0].newInstance(
                    binding[isIdeStartupKey] as Boolean,
                    binding[projectKey] as Project?,
                    binding[pluginPathKey] as String,
                    binding[pluginDisposableKey] as Disposable
                )
            } catch (e: Throwable) {
                errorReporter.addRunningError(pluginId, e)
            }
        }
    }

    private fun findClasspathAdditionsIn(lines: List<String>, pluginFolderPath: String, pluginId: String): List<File> {
        return findClasspathAdditions(
            lines,
            kotlinAddToClasspathKeyword,
            environment + Pair("PLUGIN_PATH", pluginFolderPath),
            onError = { path -> errorReporter.addLoadingError(pluginId, "Couldn't find dependency '$path'") }
        ).map { File(it) }
    }

    companion object {
        const val mainScript = "plugin.kts"
        const val testScript = "plugin-test.kts"
        const val kotlinAddToClasspathKeyword = "// " + PluginRunner.addToClasspathKeyword
        const val kotlinDependsOnPluginKeyword = "// " + PluginRunner.dependsOnPluginKeyword
        private val livePluginCompilerLibsPath = toSystemIndependentName("$livePluginPath/kotlin-compiler")

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
        ?: throw IllegalStateException("Failed to find IDE lib folder.")
    return File(ideJarPath).parentFile.filesList()
}

private fun jarFilesOf(dependentPlugins: List<String>): List<File> {
    val pluginDescriptors = pluginDescriptorsOf(dependentPlugins, onError = { throw IllegalStateException("Failed to find jar for dependent plugin '$it'.") })
    return pluginDescriptors.map { it.path }
}