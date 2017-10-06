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
import liveplugin.pluginrunner.KotlinPluginRunner.MAIN_SCRIPT
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
import org.jetbrains.kotlin.config.ContentRoot
import org.jetbrains.kotlin.config.JVMConfigurationKeys.*
import org.jetbrains.kotlin.config.KotlinSourceRoot
import org.jetbrains.kotlin.script.KotlinScriptDefinition
import java.io.File
import java.io.IOException
import kotlin.jvm.internal.Reflection

private val KOTLIN_ADD_TO_CLASSPATH_KEYWORD = "// " + PluginRunner.ADD_TO_CLASSPATH_KEYWORD
private val KOTLIN_DEPENDS_ON_PLUGIN_KEYWORD = "// " + PluginRunner.DEPENDS_ON_PLUGIN_KEYWORD

fun runPlugin(
    pathToPluginFolder: String,
    pluginId: String,
    binding: Map<String, *>,
    runOnEDTCallback: Function<Runnable, Void>,
    errorReporter: ErrorReporter,
    environment: MutableMap<String, String>
) {
    val rootDisposable = Disposer.newDisposable()

    try {
        val mainScriptUrl = asUrl(findScriptFileIn(pathToPluginFolder, MAIN_SCRIPT))
        val dependentPlugins = findPluginDependencies(readLines(mainScriptUrl), KOTLIN_DEPENDS_ON_PLUGIN_KEYWORD)
        val pathsToAdd = findClasspathAdditions(readLines(mainScriptUrl), KOTLIN_ADD_TO_CLASSPATH_KEYWORD, environment) { path ->
            errorReporter.addLoadingError(pluginId, "Couldn't find dependency '$path'")
            null
        }
        val pluginFolderUrl = "file:///$pathToPluginFolder/" // prefix with "file:///" so that unix-like paths work on windows
        pathsToAdd.add(pluginFolderUrl)

        val configuration = createCompilerConfiguration(pathToPluginFolder, pluginId, pathsToAdd, dependentPlugins, errorReporter)

        environment.put("PLUGIN_PATH", pathToPluginFolder)

        val kotlinEnvironment = KotlinCoreEnvironment.createForProduction(rootDisposable, configuration, JVM_CONFIG_FILES)
        val state = KotlinToJVMBytecodeCompiler.analyzeAndGenerate(kotlinEnvironment) ?: throw CompilationException("Compiler returned empty state.", null, null)

        val classLoader = createClassLoaderWithDependencies(pathsToAdd, dependentPlugins, mainScriptUrl, pluginId, errorReporter)
        val generatedClassLoader = GeneratedClassLoader(state.factory, classLoader)

        for (ktFile in kotlinEnvironment.getSourceFiles()) {
            if (ktFile.name == MAIN_SCRIPT) {
                val ktScript = ktFile.script!!
                val aClass = generatedClassLoader.loadClass(ktScript.fqName.asString())
                runOnEDTCallback.`fun`(Runnable {
                   try {
                       // Arguments below must match constructor of liveplugin.pluginrunner.kotlin.KotlinScriptTemplate class.
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
    } catch (e: ClassNotFoundException) {
        errorReporter.addLoadingError(pluginId, "Error compiling script. " + e.message)
    } catch (e: Throwable) {
        errorReporter.addLoadingError(pluginId, "Internal error compiling script. " + e.message)
    } finally {
        rootDisposable.dispose()
    }
}
private fun createCompilerConfiguration(pathToPluginFolder: String, pluginId: String,
                                        pathsToAdd: List<String>, dependentPlugins: List<String>,
                                        errorReporter: ErrorReporter): CompilerConfiguration {
    val configuration = CompilerConfiguration()
    configuration.put(MODULE_NAME, "LivePluginScript")
    configuration.put<MessageCollector>(MESSAGE_COLLECTOR_KEY, newMessageCollector(pluginId, errorReporter))
    configuration.put(RETAIN_OUTPUT_IN_MEMORY, true)
    configuration.add(SCRIPT_DEFINITIONS, KotlinScriptDefinition(Reflection.createKotlinClass(KotlinScriptTemplate::class.java)))

    configuration.add<ContentRoot>(CONTENT_ROOTS, KotlinSourceRoot(pathToPluginFolder))

    for (file in ideJdkClassesRoots()) {
        configuration.add<ContentRoot>(CONTENT_ROOTS, JvmClasspathRoot(file))
    }
    for (file in listFilesIn(ideLibFolder())) {
        configuration.add<ContentRoot>(CONTENT_ROOTS, JvmClasspathRoot(file))
    }
    for (file in listFilesIn(File(LIVEPLUGIN_LIBS_PATH))) {
        configuration.add<ContentRoot>(CONTENT_ROOTS, JvmClasspathRoot(file))
    }
    for (path in pathsToAdd) {
        configuration.add<ContentRoot>(CONTENT_ROOTS, JvmClasspathRoot(File(path)))
    }
    for (file in jarFilesOf(dependentPlugins)) {
        configuration.add<ContentRoot>(CONTENT_ROOTS, JvmClasspathRoot(file))
    }

    // It might be worth using:
    //	    configuration.put(JVMConfigurationKeys.OUTPUT_DIRECTORY, saveClassesDir)
    // But compilation performance doesn't seem to be the biggest problem right now.

    return configuration
}

private fun ideJdkClassesRoots(): List<File> {
    return JavaSdkUtil.getJdkClassesRoots(File(System.getProperty("java.home")), true)
}

private fun ideLibFolder(): File {
    val ideJarPath = PathManager.getJarPathForClass(IntelliJLaf::class.java) ?: throw IllegalStateException("Failed to find IDE lib folder.")
    return File(ideJarPath).parentFile
}

private fun jarFilesOf(dependentPlugins: List<String>): List<File> {
    val pluginDescriptors = pluginDescriptorsOf(dependentPlugins) { it -> throw IllegalStateException("Failed to find jar for dependent plugin '$it'.") }
    return ContainerUtil.map<IdeaPluginDescriptor, File>(pluginDescriptors) { it -> it.getPath() }
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
