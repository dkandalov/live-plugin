package liveplugin.pluginrunner.kotlin

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.ui.laf.IntelliJLaf
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.util.Function
import com.intellij.util.containers.ContainerUtil
import liveplugin.LivePluginAppComponent.LIVEPLUGIN_LIBS_PATH
import liveplugin.MyFileUtil.*
import liveplugin.pluginrunner.ErrorReporter
import liveplugin.pluginrunner.KotlinPluginRunner.Companion.MAIN_SCRIPT
import liveplugin.pluginrunner.KotlinScriptTemplate
import liveplugin.pluginrunner.PluginRunner
import liveplugin.pluginrunner.PluginRunner.ClasspathAddition.*
import org.jetbrains.jps.model.java.impl.JavaSdkUtil
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.ERROR
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.EXCEPTION
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer.PLAIN_FULL_PATHS
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles.JVM_CONFIG_FILES
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler
import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot
import org.jetbrains.kotlin.codegen.CompilationException
import org.jetbrains.kotlin.codegen.GeneratedClassLoader
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.config.CommonConfigurationKeys.MODULE_NAME
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys.*
import org.jetbrains.kotlin.config.KotlinSourceRoot
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import java.io.File
import java.io.IOException
import kotlin.jvm.internal.Reflection


@Suppress("unused") // Used via reflection.
fun compilePlugin(sourceRoot: String, classpath: List<String>, compilerOutputPath: String): List<String> {
    val rootDisposable = Disposer.newDisposable()

    val messageCollector = ErrorMessageCollector()
    val configuration = createCompilerConfiguration(sourceRoot, classpath, compilerOutputPath, messageCollector)
    val kotlinEnvironment = KotlinCoreEnvironment.createForProduction(rootDisposable, configuration, JVM_CONFIG_FILES)
    val state = KotlinToJVMBytecodeCompiler.analyzeAndGenerate(kotlinEnvironment)

    return when {
        messageCollector.hasErrors() -> messageCollector.errors
        state == null -> listOf("Compiler returned empty state.")
        else -> emptyList()
    }
}

private class ErrorMessageCollector : MessageCollector {
    val errors = ArrayList<String>()

    override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageLocation?) {
        if (severity == ERROR || severity == EXCEPTION) {
            errors.add(PLAIN_FULL_PATHS.render(severity, message, location))
        }
    }

    override fun clear() {
        errors.clear()
    }

    override fun hasErrors() = errors.isNotEmpty()
}


fun runPlugin(
    pathToPluginFolder: String,
    pluginId: String,
    binding: Map<String, *>,
    runOnEDTCallback: Function<Runnable, Void>,
    errorReporter: ErrorReporter,
    environment: MutableMap<String, String>
) {
    val kotlinAddToClasspathKeyword = "// " + PluginRunner.ADD_TO_CLASSPATH_KEYWORD
    val kotlinDependsOnPluginKeyword = "// " + PluginRunner.DEPENDS_ON_PLUGIN_KEYWORD
    val rootDisposable = Disposer.newDisposable()

    try {
        val mainScriptUrl = asUrl(findScriptFileIn(pathToPluginFolder, MAIN_SCRIPT))
        val dependentPlugins = findPluginDependencies(readLines(mainScriptUrl), kotlinDependsOnPluginKeyword)
        val libPaths = findClasspathAdditions(readLines(mainScriptUrl), kotlinAddToClasspathKeyword, environment) { path ->
            errorReporter.addLoadingError(pluginId, "Couldn't find dependency '$path'")
            null
        }
        libPaths.addAll(jarFilesOf(dependentPlugins).map{ it.absolutePath })
        libPaths.addAll(listFilesIn(File(LIVEPLUGIN_LIBS_PATH)).map{ it.absolutePath })
        libPaths.addAll(ideJdkClassesRoots().map{ it.absolutePath })
        libPaths.addAll(listFilesIn(ideLibFolder()).map{ it.absolutePath })
        libPaths.add("file:///$pathToPluginFolder/") // prefix with "file:///" so that unix-like paths work on windows

        val configuration = createCompilerConfiguration(pathToPluginFolder, libPaths, "", newMessageCollector(pluginId, errorReporter))

        environment.put("PLUGIN_PATH", pathToPluginFolder) // TODO looks like it's never used

        val kotlinEnvironment = KotlinCoreEnvironment.createForProduction(rootDisposable, configuration, JVM_CONFIG_FILES)
        val state = KotlinToJVMBytecodeCompiler.analyzeAndGenerate(kotlinEnvironment) ?: throw CompilationException("Compiler returned empty state.", null, null)

        val classLoader = createClassLoaderWithDependencies(libPaths, dependentPlugins, mainScriptUrl, pluginId, errorReporter)
        val generatedClassLoader = GeneratedClassLoader(state.factory, classLoader)

        for (ktFile in kotlinEnvironment.getSourceFiles()) {
            if (ktFile.name == MAIN_SCRIPT) {
                val ktScript = ktFile.script!!
                val aClass = generatedClassLoader.loadClass(ktScript.fqName.asString())
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
                   } catch (e: Exception) {
                       errorReporter.addRunningError(pluginId, e)
                   }
                })
            }
        }

    } catch (e: IOException) {
        errorReporter.addLoadingError(pluginId, "Error creating scripting engine. " + e.message)
    } catch (e: CompilationException) {
        errorReporter.addLoadingError(pluginId, "Error compiling script. " + e.message)
    } catch (e: Throwable) {
        errorReporter.addLoadingError(pluginId, "Internal error compiling script. " + e.message)
    } finally {
        rootDisposable.dispose()
    }
}

private fun createCompilerConfiguration(
    sourceRoot: String,
    classpath: List<String>,
    compilerOutputPath: String,
    messageCollector: MessageCollector
): CompilerConfiguration {
    val configuration = CompilerConfiguration()
    configuration.put(MODULE_NAME, "LivePluginScript")
    configuration.put(MESSAGE_COLLECTOR_KEY, messageCollector)
    configuration.add(SCRIPT_DEFINITIONS, KotlinScriptDefinition(Reflection.createKotlinClass(KotlinScriptTemplate::class.java)))

    configuration.add(CONTENT_ROOTS, KotlinSourceRoot(sourceRoot))

    for (path in classpath) {
        configuration.add(CONTENT_ROOTS, JvmClasspathRoot(File(path)))
    }

    configuration.put(RETAIN_OUTPUT_IN_MEMORY, false)
    configuration.put(OUTPUT_DIRECTORY, File(compilerOutputPath))

    return configuration
}

fun ideJdkClassesRoots(): List<File> =
    JavaSdkUtil.getJdkClassesRoots(File(System.getProperty("java.home")), true)

fun ideLibFolder(): File {
    val ideJarPath = PathManager.getJarPathForClass(IntelliJLaf::class.java) ?: throw IllegalStateException("Failed to find IDE lib folder.")
    return File(ideJarPath).parentFile
}

fun jarFilesOf(dependentPlugins: List<String>): List<File> {
    val pluginDescriptors = pluginDescriptorsOf(dependentPlugins) { it -> throw IllegalStateException("Failed to find jar for dependent plugin '$it'.") }
    return ContainerUtil.map<IdeaPluginDescriptor, File>(pluginDescriptors) { it -> it.path }
}

private fun newMessageCollector(pluginId: String, errorReporter: ErrorReporter): MessageCollector {
    return object: MessageCollector {
        internal var hasErrors = false

        override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageLocation?) {
            if (severity == ERROR || severity == EXCEPTION) {
                errorReporter.addLoadingError(pluginId, PLAIN_FULL_PATHS.render(severity, message, location))
                hasErrors = true
            }
        }

        override fun hasErrors() = hasErrors

        override fun clear() {}
    }
}
