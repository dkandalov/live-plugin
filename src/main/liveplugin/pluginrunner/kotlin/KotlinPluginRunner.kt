package liveplugin.pluginrunner.kotlin

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.util.lang.UrlClassLoader
import liveplugin.*
import liveplugin.IdeUtil.unscrambleThrowable
import liveplugin.Result.Failure
import liveplugin.Result.Success
import liveplugin.pluginrunner.*
import liveplugin.pluginrunner.AnError.LoadingError
import liveplugin.pluginrunner.AnError.RunningError
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.createClassLoaderWithDependencies
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.findClasspathAdditions
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.findPluginDescriptorsOfDependencies
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.withTransitiveDependencies
import liveplugin.pluginrunner.kotlin.KotlinPluginRunner.Companion.kotlinAddToClasspathKeyword
import liveplugin.pluginrunner.kotlin.KotlinPluginRunner.Companion.kotlinDependsOnPluginKeyword
import org.apache.commons.codec.digest.MurmurHash3
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
    data class ExecutableKotlinPlugin(val pluginClass: Class<*>) : ExecutablePlugin

    override fun setup(plugin: LivePlugin): Result<ExecutablePlugin, AnError> {
        val mainScript = plugin.path.find(scriptName)
            ?: return LoadingError(message = "Startup script $scriptName was not found.").asFailure()

        val pluginDescriptorsOfDependencies = findPluginDescriptorsOfDependencies(mainScript.readLines(), kotlinDependsOnPluginKeyword)
            .map { it.onFailure { (message) -> return Failure(LoadingError(message)) } }
            .onEach { if (!it.isEnabled) return Failure(LoadingError("Dependent plugin '${it.pluginId}' is disabled")) }
            .withTransitiveDependencies()

        val environment = systemEnvironment + Pair("PLUGIN_PATH", plugin.path.value)
        val additionalClasspath = findClasspathAdditions(mainScript.readLines(), kotlinAddToClasspathKeyword, environment)
            .flatMap { it.onFailure { (path) -> return Failure(LoadingError("Couldn't find dependency '$path.'")) } }

        val compilerOutput = File("${LivePluginPaths.livePluginsCompiledPath}/${plugin.id}")

        val srcHashCode = SrcHashCode(plugin.path, compilerOutput.toFilePath())
        if (srcHashCode.needsUpdate()) {
            compilerOutput.deleteRecursively()

            KotlinPluginCompiler()
                .compile(plugin.path.value, pluginDescriptorsOfDependencies, additionalClasspath, compilerOutput)
                .onFailure { (reason) -> return Failure(LoadingError(reason)) }

            srcHashCode.update()
        }

        val pluginClass = try {
            val runtimeClassPath =
                listOf(compilerOutput) +
                livePluginLibAndSrcFiles() +
                additionalClasspath
            val classLoader = createClassLoaderWithDependencies(runtimeClassPath, pluginDescriptorsOfDependencies, plugin)
                .onFailure { return Failure(LoadingError(it.reason.message)) }
            classLoader.loadClass("Plugin")
        } catch (e: Throwable) {
            return Failure(LoadingError("Error while loading plugin class.", e))
        }
        return ExecutableKotlinPlugin(pluginClass).asSuccess()
    }

    override fun run(executablePlugin: ExecutablePlugin, binding: Binding): Result<Unit, AnError> {
        val pluginClass = (executablePlugin as ExecutableKotlinPlugin).pluginClass
        return try {
            // Arguments below must match constructor of LivePluginScript class.
            // There doesn't seem to be a way to add binding as Map, therefore, hardcoding them.
            pluginClass.constructors[0].newInstance(binding.isIdeStartup, binding.project, binding.pluginPath, binding.pluginDisposable)
            Success(Unit)
        } catch (e: Throwable) {
            Failure(RunningError(e))
        }
    }

    companion object {
        const val mainScript = "plugin.kts"
        const val testScript = "plugin-test.kts"
        const val kotlinAddToClasspathKeyword = "// add-to-classpath "
        const val kotlinDependsOnPluginKeyword = "// depends-on-plugin "

        val main = KotlinPluginRunner(mainScript)
        val test = KotlinPluginRunner(testScript)
    }
}

private class KotlinPluginCompiler {
    fun compile(
        pluginFolderPath: String,
        pluginDescriptorsOfDependencies: List<IdeaPluginDescriptor>,
        additionalClasspath: List<File>,
        compilerOutput: File
    ): Result<Unit, String> {
        // Ideally, the compilerClasspath could be replaced by LivePluginScriptConfig
        // (which implicitly sets up the compiler classpath via kotlin scripting) but
        // this approach doesn't seem to work e.g. for ideLibFiles() and resolving dependent plugins.
        val compilerClasspath: List<File> =
            ideLibFiles() +
            livePluginLibAndSrcFiles() +
            pluginDescriptorsOfDependencies.flatMap { it.toLibFiles() } +
            // Put kotlin compiler libs after plugin dependencies because if there is Kotlin plugin in plugin dependencies,
            // it somehow picks up wrong PSI classes from kotlin-compiler-embeddable.jar.
            // E.g. "type mismatch: inferred type is KtVisitor<Void, Void> but PsiElementVisitor was expected".
            livePluginKotlinCompilerLibFiles() +
            additionalClasspath +
            File(pluginFolderPath)

        val compilerRunnerClass = compilerClassLoader.loadClass("liveplugin.pluginrunner.kotlin.compiler.EmbeddedCompilerRunnerKt")
        val compilePluginMethod = compilerRunnerClass.declaredMethods.find { it.name == "compile" }!!
        return try {
            // Note that arguments passed via reflection CANNOT use pure Kotlin types
            // because compiler uses different classloader to load Kotlin so classes won't be compatible
            // (it's ok though to use types like kotlin.String which becomes java.lang.String at runtime).
            @Suppress("UNCHECKED_CAST")
            val compilationErrors = compilePluginMethod.invoke(
                null,
                pluginFolderPath,
                compilerClasspath,
                compilerOutput,
                LivePluginScriptForCompilation::class.java
            ) as List<String>

            if (compilationErrors.isNotEmpty()) {
                return Failure("Error compiling script.\n${compilationErrors.joinToString("\n")}")
            } else {
                Unit.asSuccess()
            }
        } catch (e: IOException) {
            return Failure("Error creating scripting engine.\n${unscrambleThrowable(e)}")
        } catch (e: Throwable) {
            // Don't depend directly on `CompilationException` because it's part of Kotlin plugin
            // and LivePlugin should be able to run kotlin scripts without it
            val reason = if (e.javaClass.canonicalName == "org.jetbrains.kotlin.codegen.CompilationException") {
                "Error compiling script.\n${unscrambleThrowable(e)}"
            } else {
                "Internal error compiling script.\n${unscrambleThrowable(e)}"
            }
            return Failure(reason)
        }
    }

    companion object {
        private val compilerClassLoader by lazy {
            UrlClassLoader.build()
                .files(ideJdkClassesRoots() + livePluginKotlinCompilerLibFiles().map(File::toPath))
                .noPreload()
                .allowBootstrapResources()
                .useCache()
                .get()
        }
    }
}

fun ideLibFiles() = LivePluginPaths.ideJarsPath.listFiles().map { it.toFile() }

fun dependenciesOnOtherPluginsForHighlighting(scriptText: List<String>): List<File> =
    findPluginDescriptorsOfDependencies(scriptText, kotlinDependsOnPluginKeyword)
        .filterIsInstance<Success<IdeaPluginDescriptor>>() // Ignore unresolved dependencies because they will be checked before runnig plugin anyway.
        .map { it.value }.withTransitiveDependencies()
        .flatMap { it.toLibFiles() }

fun findClasspathAdditionsForHighlightingAndScriptTemplate(scriptText: List<String>, scriptFolderPath: String): List<File> =
    findClasspathAdditions(scriptText, kotlinAddToClasspathKeyword, systemEnvironment() + Pair("PLUGIN_PATH", scriptFolderPath))
        .filterIsInstance<Success<List<File>>>() // Ignore unresolved dependencies because they will be checked before runnig plugin anyway.
        .flatMap { it.value }

fun livePluginLibAndSrcFiles() =
    LivePluginPaths.livePluginLibPath.listFiles().map { it.toFile() }

private fun livePluginKotlinCompilerLibFiles() =
    (LivePluginPaths.livePluginPath + "kotlin-compiler").listFiles().map { it.toFile() }

private fun IdeaPluginDescriptor.toLibFiles() =
    (pluginPath.toFilePath() + "lib").listFiles {
        // Exclusion specifically for Kotlin plugin which includes kotlin-compiler-plugins jars
        // which seem to be compiled with IJ API and are not compatible with actual Kotlin compilers.
        !it.name.contains("compiler-plugin")
    }.map { it.toFile() }

private fun ideJdkClassesRoots() = JavaSdkUtil.getJdkClassesRoots(File(System.getProperty("java.home")).toPath(), true)

class SrcHashCode(srcDir: FilePath, compilerOutputDir: FilePath) {
    private val hashFilePath = compilerOutputDir + hashFileName
    private val hash = calculateSourceCodeHash(srcDir)

    fun needsUpdate() = !hashFilePath.exists() || hashFilePath.toFile().readText().toLongOrNull() != hash

    fun update() = hashFilePath.toFile().writeText(hash.toString())

    fun calculateSourceCodeHash(srcDirPath: FilePath) =
        srcDirPath.allFiles()
            .filter { it.extension == "kt" || it.extension == "kts" }
            .fold(0L) { hash, file ->
                MurmurHash3.hash32(hash, (file.name + file.readText()).hashCode().toLong()).toLong()
            }

    companion object {
        const val hashFileName = "hash32.txt"
    }
}
