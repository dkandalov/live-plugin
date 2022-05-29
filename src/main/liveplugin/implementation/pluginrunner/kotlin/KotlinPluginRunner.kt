package liveplugin.implementation.pluginrunner.kotlin

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.project.Project
import com.intellij.util.lang.UrlClassLoader
import liveplugin.implementation.LivePlugin
import liveplugin.implementation.LivePluginPaths.ideJarsPath
import liveplugin.implementation.LivePluginPaths.livePluginLibPath
import liveplugin.implementation.LivePluginPaths.livePluginPath
import liveplugin.implementation.LivePluginPaths.livePluginsCompiledPath
import liveplugin.implementation.common.*
import liveplugin.implementation.common.IdeUtil.unscrambleThrowable
import liveplugin.implementation.common.Result.Success
import liveplugin.implementation.pluginrunner.*
import liveplugin.implementation.pluginrunner.PluginDependencies.createClassLoaderWithDependencies
import liveplugin.implementation.pluginrunner.PluginDependencies.findClasspathAdditions
import liveplugin.implementation.pluginrunner.PluginDependencies.findPluginDescriptorsOfDependencies
import liveplugin.implementation.pluginrunner.PluginDependencies.withTransitiveDependencies
import liveplugin.implementation.pluginrunner.kotlin.KotlinPluginRunner.Companion.kotlinAddToClasspathKeyword
import liveplugin.implementation.pluginrunner.kotlin.KotlinPluginRunner.Companion.kotlinDependsOnPluginKeyword
import org.apache.commons.codec.digest.MurmurHash3
import org.jetbrains.jps.model.java.impl.JavaSdkUtil
import java.io.File
import java.io.IOException
import kotlin.io.path.absolutePathString

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
 *  - `kotlin-compiler-embeddable` has some classes with duplicate names renamed but not all of them
 *  - kotlin compiler attempts to initialise some global variables which are already initialised by IDE
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

    override fun setup(plugin: LivePlugin, project: Project?): Result<ExecutablePlugin, SetupError> {
        val mainScript = plugin.path.find(scriptName)
            ?: return SetupError(message = "Startup script $scriptName was not found.").asFailure()

        val pluginDescriptorsOfDependencies = findPluginDescriptorsOfDependencies(mainScript.readLines(), kotlinDependsOnPluginKeyword)
            .map { it.onFailure { (message) -> return SetupError(message).asFailure() } }
            .onEach { if (!it.isEnabled) return SetupError("Dependent plugin '${it.pluginId}' is disabled").asFailure() }
            .withTransitiveDependencies()

        val environment = systemEnvironment + Pair("PLUGIN_PATH", plugin.path.value) + Pair("PROJECT_PATH", project?.basePath ?: "PROJECT_PATH")
        val additionalClasspath = findClasspathAdditions(mainScript.readLines(), kotlinAddToClasspathKeyword, environment)
            .flatMap { it.onFailure { (path) -> return SetupError("Couldn't find dependency '$path.'").asFailure() } }

        // Add plugin path hashcode in case there are plugins with the same id in different locations.
        val compilerOutput = livePluginsCompiledPath + "${plugin.id}-${plugin.path.value.hashCode()}"

        val srcHashCode = SrcHashCode(plugin.path, compilerOutput)
        if (srcHashCode.needsUpdate()) {
            compilerOutput.toFile().deleteRecursively()

            KotlinPluginCompiler()
                .compile(plugin.path.value, pluginDescriptorsOfDependencies, additionalClasspath, compilerOutput.toFile())
                .onFailure { (reason) -> return SetupError(reason).asFailure() }

            srcHashCode.update()
        }

        val pluginClass = try {
            val runtimeClassPath =
                listOf(compilerOutput.toFile()) +
                livePluginLibAndSrcFiles() +
                additionalClasspath
            val classLoader = createClassLoaderWithDependencies(runtimeClassPath, pluginDescriptorsOfDependencies, plugin)
                .onFailure { return SetupError(it.reason.message).asFailure() }
            classLoader.loadClass("Plugin")
        } catch (e: Throwable) {
            return SetupError("Error while loading plugin class.", e).asFailure()
        }
        return ExecutableKotlinPlugin(pluginClass).asSuccess()
    }

    override fun run(executablePlugin: ExecutablePlugin, binding: Binding): Result<Unit, RunningError> {
        val pluginClass = (executablePlugin as ExecutableKotlinPlugin).pluginClass
        return try {
            // Arguments below must match constructor of LivePluginScript class.
            // There doesn't seem to be a way to add binding as Map, therefore, hardcoding them.
            pluginClass.constructors[0].newInstance(binding.isIdeStartup, binding.project, binding.pluginPath, binding.pluginDisposable)
            Unit.asSuccess()
        } catch (e: Throwable) {
            RunningError(e).asFailure()
        }
    }

    companion object {
        const val kotlinScriptFile = "plugin.kts"
        const val kotlinTestScriptFile = "plugin-test.kts"
        const val kotlinAddToClasspathKeyword = "// add-to-classpath "
        const val kotlinDependsOnPluginKeyword = "// depends-on-plugin "

        val mainKotlinPluginRunner = KotlinPluginRunner(kotlinScriptFile)
        val testKotlinPluginRunner = KotlinPluginRunner(kotlinTestScriptFile)
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
            pluginDescriptorsOfDependencies.flatMap { descriptor -> descriptor.toLibFiles() } +
            // Put kotlin compiler libs after plugin dependencies because if there is Kotlin plugin in plugin dependencies,
            // it somehow picks up wrong PSI classes from kotlin-compiler-embeddable.jar.
            // E.g. "type mismatch: inferred type is KtVisitor<Void, Void> but PsiElementVisitor was expected".
            livePluginKotlinCompilerLibFiles() +
            additionalClasspath +
            File(pluginFolderPath)

        val compilerRunnerClass = compilerClassLoader.loadClass("liveplugin.implementation.kotlin.EmbeddedCompilerKt")
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
                return "Error compiling script.\n${compilationErrors.joinToString("\n")}".asFailure()
            } else {
                Unit.asSuccess()
            }
        } catch (e: IOException) {
            return "Error creating scripting engine.\n${unscrambleThrowable(e)}".asFailure()
        } catch (e: Throwable) {
            // Don't depend directly on `CompilationException` because it's part of Kotlin plugin
            // and LivePlugin should be able to run kotlin scripts without it
            val reason = if (e.javaClass.canonicalName == "org.jetbrains.kotlin.codegen.CompilationException") {
                "Error compiling script.\n${unscrambleThrowable(e)}"
            } else {
                "LivePlugin error while compiling script.\n${unscrambleThrowable(e)}"
            }
            return reason.asFailure()
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

fun ideLibFiles() = ideJarsPath
    .listFiles {
        // Filter because Kotlin compiler complains about non-zip files.
        it.isDirectory || it.extension == "jar" || it.extension == "zip"
    }
    .map { it.toFile() }

fun dependenciesOnOtherPluginsForHighlighting(scriptText: List<String>): List<File> =
    findPluginDescriptorsOfDependencies(scriptText, kotlinDependsOnPluginKeyword)
        .filterIsInstance<Success<IdeaPluginDescriptor>>() // Ignore unresolved dependencies because they will be checked before running plugin anyway.
        .map { it.value }.withTransitiveDependencies()
        .flatMap { it.toLibFiles() }

fun findClasspathAdditionsForHighlightingAndScriptTemplate(scriptText: List<String>, scriptFolderPath: String): List<File> =
    findClasspathAdditions(scriptText, kotlinAddToClasspathKeyword, systemEnvironment() + Pair("PLUGIN_PATH", scriptFolderPath))
        .filterIsInstance<Success<List<File>>>() // Ignore unresolved dependencies because they will be checked before running plugin anyway.
        .flatMap { it.value }

fun livePluginLibAndSrcFiles() =
    livePluginLibPath.listFiles().map { it.toFile() }

private fun livePluginKotlinCompilerLibFiles() =
    (livePluginPath + "kotlin-compiler").listFiles().map { it.toFile() }

private fun IdeaPluginDescriptor.toLibFiles() =
    (pluginPath.absolutePathString().toFilePath() + "lib").listFiles {
        // Exclusion specifically for Kotlin plugin which includes kotlin-compiler-plugins jars
        // which seem to be compiled with IJ API and are not compatible with actual Kotlin compilers.
        "compiler-plugin" !in it.name
    }.map { it.toFile() }

private fun ideJdkClassesRoots() = JavaSdkUtil.getJdkClassesRoots(File(System.getProperty("java.home")).toPath(), true)

class SrcHashCode(srcDir: FilePath, compilerOutputDir: FilePath) {
    private val hashFilePath = compilerOutputDir + hashFileName
    private val hash = calculateSourceCodeHash(srcDir)

    fun needsUpdate() = !hashFilePath.exists() || hashFilePath.readText().toLongOrNull() != hash

    fun update() = hashFilePath.toFile().writeText(hash.toString())

    private fun calculateSourceCodeHash(srcDirPath: FilePath) =
        srcDirPath.allFiles()
            .filter { it.extension == "kt" || it.extension == "kts" }
            .fold(0L) { hash, file ->
                MurmurHash3.hash32(hash, (file.name + file.readText()).hashCode().toLong()).toLong()
            }

    companion object {
        const val hashFileName = "hash32.txt"
    }
}
