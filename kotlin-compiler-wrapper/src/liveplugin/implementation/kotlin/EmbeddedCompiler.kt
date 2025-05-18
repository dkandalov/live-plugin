@file:Suppress("DEPRECATION")

package liveplugin.implementation.kotlin

import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys.CONTENT_ROOTS
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY
import org.jetbrains.kotlin.cli.common.config.KotlinSourceRoot
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.ERROR
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.EXCEPTION
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageCollectorUtil
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer.PLAIN_FULL_PATHS
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles.JVM_CONFIG_FILES
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler
import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar.Companion.COMPILER_PLUGIN_REGISTRARS
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar.Companion.PLUGIN_COMPONENT_REGISTRARS
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.CommonConfigurationKeys.ALLOW_ANY_SCRIPTS_IN_SOURCE_ROOTS
import org.jetbrains.kotlin.config.CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS
import org.jetbrains.kotlin.config.CommonConfigurationKeys.MODULE_NAME
import org.jetbrains.kotlin.config.CommonConfigurationKeys.REPORT_OUTPUT_FILES
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys.*
import org.jetbrains.kotlin.config.JvmTarget.JVM_17
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.scripting.configuration.ScriptingConfigurationKeys.DISABLE_SCRIPTING_PLUGIN_OPTION
import org.jetbrains.kotlin.scripting.configuration.ScriptingConfigurationKeys.SCRIPT_DEFINITIONS
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.util.ServiceLoaderLite
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import kotlin.reflect.KClass
import kotlin.script.experimental.host.ScriptingHostConfiguration
import kotlin.script.experimental.host.configurationDependencies
import kotlin.script.experimental.host.getScriptingClass
import kotlin.script.experimental.jvm.JvmDependency
import kotlin.script.experimental.jvm.JvmGetScriptingClass

fun compile(
    sourceRoot: String,
    classpath: List<File>,
    jrePath: File,
    outputDirectory: File,
    livePluginScriptClass: Class<*>
): List<String> {
    val rootDisposable = Disposer.newDisposable()
    try {
        val messageCollector = ErrorMessageCollector()
        val configuration = createCompilerConfiguration(sourceRoot, classpath, jrePath, outputDirectory, messageCollector, livePluginScriptClass.kotlin)
        val kotlinEnvironment = KotlinCoreEnvironment.createForProduction(rootDisposable, configuration, JVM_CONFIG_FILES)
        val state = KotlinToJVMBytecodeCompiler.analyzeAndGenerate(kotlinEnvironment).also { it?.destroy() }

        return when {
            messageCollector.hasErrors() -> messageCollector.errors
            state == null                -> listOf("Compiler returned empty state.")
            else                         -> emptyList()
        }
    } finally {
        rootDisposable.dispose()
    }
}

private class ErrorMessageCollector : MessageCollector {
    val errors = ArrayList<String>()

    override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?) {
        if (severity == ERROR || severity == EXCEPTION) errors.add(PLAIN_FULL_PATHS.render(severity, message, location))
    }

    override fun clear() = errors.clear()
    override fun hasErrors() = errors.isNotEmpty()
}

@OptIn(ExperimentalCompilerApi::class)
private fun createCompilerConfiguration(
    sourceRoot: String,
    classpath: List<File>,
    jrePath: File,
    outputDirectory: File,
    messageCollector: MessageCollector,
    livePluginScriptClass: KClass<*>
) = CompilerConfiguration().apply {
    put(MODULE_NAME, "KotlinCompilerWrapperModule")
    put(MESSAGE_COLLECTOR_KEY, messageCollector)
    add(
        SCRIPT_DEFINITIONS,
        ScriptDefinition.FromTemplate(
            ScriptingHostConfiguration {
                configurationDependencies.put(listOf(JvmDependency(classpath)))
                getScriptingClass(JvmGetScriptingClass())
            },
            livePluginScriptClass
        )
    )
    put(DISABLE_SCRIPTING_PLUGIN_OPTION, false)
    put(DISABLE_STANDARD_SCRIPT_DEFINITION, true)
    put(ALLOW_ANY_SCRIPTS_IN_SOURCE_ROOTS, true)

    add(CONTENT_ROOTS, KotlinSourceRoot(path = sourceRoot, isCommon = false, hmppModuleName = null))
    classpath.forEach { file ->
        add(CONTENT_ROOTS, JvmClasspathRoot(file))
    }

    // Based on org.jetbrains.kotlin.script.ScriptTestUtilKt#loadScriptingPlugin
    addAll(
        PLUGIN_COMPONENT_REGISTRARS,
        loadComponentRegistrars(classpath.map { it.path }, messageCollector)
            // Exclude AndroidComponentRegistrar because its API is not compatible with kotlin-compiler-embedded (see https://youtrack.jetbrains.com/issue/KT-43086)
            .filter { "AndroidComponentRegistrar" !in it.javaClass.name }
    )
    addAll(
        COMPILER_PLUGIN_REGISTRARS,
        loadCompilerPluginRegistrars(classpath.map { it.path }, messageCollector)
    )

    put(JDK_HOME, jrePath)
    put(JVM_TARGET, JVM_17)
    put(RETAIN_OUTPUT_IN_MEMORY, false)
    put(OUTPUT_DIRECTORY, outputDirectory)
    put(REPORT_OUTPUT_FILES, true)
    put(LANGUAGE_VERSION_SETTINGS, LanguageVersionSettingsImpl(LanguageVersion.KOTLIN_2_1, ApiVersion.KOTLIN_2_1))
}

// Based on modified version of org.jetbrains.kotlin.cli.jvm.plugins.PluginCliParser.loadPluginsSafe
@OptIn(ExperimentalCompilerApi::class)
private fun loadComponentRegistrars(pluginClasspaths: Iterable<String>, messageCollector: MessageCollector): List<ComponentRegistrar> =
    try {
        val classLoader = URLClassLoader(
            pluginClasspaths.map { File(it).toURI().toURL() }.toTypedArray<URL?>(),
            ErrorMessageCollector::class.java.classLoader
        )
        ServiceLoaderLite.loadImplementations(ComponentRegistrar::class.java, classLoader)
    } catch (t: Throwable) {
        MessageCollectorUtil.reportException(messageCollector, t)
        emptyList()
    }

@OptIn(ExperimentalCompilerApi::class)
private fun loadCompilerPluginRegistrars(pluginClasspaths: Iterable<String>, messageCollector: MessageCollector): List<CompilerPluginRegistrar> =
    try {
        val classLoader = URLClassLoader(
            pluginClasspaths.map { File(it).toURI().toURL() }.toTypedArray<URL?>(),
            ErrorMessageCollector::class.java.classLoader
        )
        ServiceLoaderLite.loadImplementations(CompilerPluginRegistrar::class.java, classLoader)
    } catch (t: Throwable) {
        MessageCollectorUtil.reportException(messageCollector, t)
        emptyList()
    }
