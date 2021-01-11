package liveplugin.pluginrunner.kotlin

import com.intellij.util.lang.UrlClassLoader
import liveplugin.IdeUtil.unscrambleThrowable
import liveplugin.LivePluginPaths
import liveplugin.find
import liveplugin.pluginrunner.*
import liveplugin.pluginrunner.AnError.LoadingError
import liveplugin.pluginrunner.AnError.RunningError
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.createClassLoaderWithDependencies
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.findClasspathAdditions
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.findDependenciesOnIdePlugins
import liveplugin.pluginrunner.Result.Failure
import liveplugin.pluginrunner.Result.Success
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

    override fun runPlugin(plugin: LivePlugin, binding: Binding, runOnEDT: (() -> Result<Unit, AnError>) -> Result<Unit, AnError>): Result<Unit, AnError> {
        val mainScriptFile = plugin.path.find(mainScript)!!
        val dependenciesOnIdePlugins = findDependenciesOnIdePlugins(mainScriptFile.readLines(), kotlinDependsOnPluginKeyword)
            .onFailure { return Failure(LoadingError(plugin.id, it.reason)) }

        val environment = systemEnvironment + Pair("PLUGIN_PATH", plugin.path.value)
        val additionalClasspath = findClasspathAdditions(mainScriptFile.readLines(), kotlinAddToClasspathKeyword, environment)
            .onFailure { path -> return Failure(LoadingError(plugin.id, "Couldn't find dependency '$path'")) }

        val compilerOutput = File("${LivePluginPaths.livePluginsCompiledPath}/${plugin.id}").also { it.deleteRecursively() }

        val compilerRunnerClass = compilerClassLoader.loadClass("liveplugin.pluginrunner.kotlin.compiler.EmbeddedCompilerRunnerKt")
        compilerRunnerClass.declaredMethods.find { it.name == "compile" }!!.let { compilePluginMethod ->
            try {
                // Ideally, the compilerClasspath could be replaced by LivePluginScriptCompilationConfiguration
                // (which implicitly sets up the compiler classpath via kotlin scripting) but
                // this approach doesn't seem to work e.g. for ideLibFiles().
                val compilerClasspath: List<File> =
                    ideLibFiles() +
                    livePluginLibAndSrcFiles() +
                    livePluginKotlinCompilerLibFiles() +
                    dependenciesOnIdePlugins.flatMap { pluginDescriptor ->
                        pluginDescriptor.pluginPath.toFile().walkTopDown().filter { it.isFile }.toList()
                    } +
                    additionalClasspath +
                    plugin.path.toFile()

                // Note that arguments passed via reflection CANNOT use pure Kotlin types
                // because compiler uses different classloader to load Kotlin so classes won't be compatible
                // (it's ok though to use types like kotlin.String which becomes java.lang.String at runtime).
                @Suppress("UNCHECKED_CAST")
                val compilationErrors = compilePluginMethod.invoke(
                    null,
                    plugin.path,
                    compilerClasspath,
                    compilerOutput,
                    KotlinScriptTemplate::class.java
                ) as List<String>

                if (compilationErrors.isNotEmpty()) {
                    return Failure(LoadingError(plugin.id, "Error compiling script. " + compilationErrors.joinToString("\n")))
                }
            } catch (e: IOException) {
                return Failure(LoadingError(plugin.id, "Error creating scripting engine. ${unscrambleThrowable(e)}"))
            } catch (e: Throwable) {
                // Don't depend directly on `CompilationException` because it's part of Kotlin plugin
                // and LivePlugin should be able to run kotlin scripts without it
                val error = if (e.javaClass.canonicalName == "org.jetbrains.kotlin.codegen.CompilationException") {
                    LoadingError(plugin.id, "Error compiling script. ${unscrambleThrowable(e)}")
                } else {
                    LoadingError(plugin.id, "Internal error compiling script. ${unscrambleThrowable(e)}")
                }
                return Failure(error)
            }
        }

        val pluginClass = try {
            val runtimeClassPath =
                listOf(compilerOutput) +
                livePluginLibAndSrcFiles() +
                additionalClasspath

            val classLoader = createClassLoaderWithDependencies(runtimeClassPath, dependenciesOnIdePlugins, plugin).onFailure {
                return Failure(LoadingError(it.reason.pluginId, it.reason.message))
            }
            classLoader.loadClass("Plugin")
        } catch (e: Throwable) {
            return Failure(LoadingError(plugin.id, "Error while loading plugin class. ${unscrambleThrowable(e)}"))
        }

        return runOnEDT {
            try {
                // Arguments below must match constructor of KotlinScriptTemplate class.
                // There doesn't seem to be a way to add binding as Map, therefore, hardcoding them.
                pluginClass.constructors[0].newInstance(binding.isIdeStartup, binding.project, binding.pluginPath, binding.pluginDisposable)
                Success(Unit)
            } catch (e: Throwable) {
                Failure(RunningError(plugin.id, e))
            }
        }
    }

    companion object {
        const val mainScript = "plugin.kts"
        const val testScript = "plugin-test.kts"
        const val kotlinAddToClasspathKeyword = "// add-to-classpath "
        const val kotlinDependsOnPluginKeyword = "// depends-on-plugin "

        val main = KotlinPluginRunner(mainScript)
        val test = KotlinPluginRunner(testScript)

        private val compilerClassLoader by lazy {
            UrlClassLoader.build()
                .urls((ideJdkClassesRoots() + livePluginKotlinCompilerLibFiles()).map(File::toUrl))
                .noPreload()
                .allowBootstrapResources()
                .useCache()
                .get()
        }
    }
}

private fun ideJdkClassesRoots(): List<File> = JavaSdkUtil.getJdkClassesRoots(javaHome, true)

private val javaHome = File(System.getProperty("java.home"))

fun ideLibFiles() = LivePluginPaths.ideJarsPath.listFiles()

// This is a hack, should be configured via "// depends-on-plugin"
fun psiApiFiles() =
    (LivePluginPaths.ideJarsPath + "../plugins/java/lib/").listFiles() +
    (LivePluginPaths.ideJarsPath + "../plugins/Kotlin/lib/").listFiles()

fun livePluginLibAndSrcFiles() =
    LivePluginPaths.livePluginLibPath.listFiles()

fun livePluginKotlinCompilerLibFiles() =
    (LivePluginPaths.livePluginPath + "kotlin-compiler").listFiles()
