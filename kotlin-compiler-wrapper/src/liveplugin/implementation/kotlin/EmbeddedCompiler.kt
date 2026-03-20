package liveplugin.implementation.kotlin

import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer.PLAIN_FULL_PATHS
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.text.Charsets.UTF_8

fun compile(
    sourceDir: String,
    classpath: List<File>,
    jrePath: File,
    outputDirectory: File,
    livePluginScriptClass: Class<*>
): List<String> {
    val sourceFiles =
        File(sourceDir).walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "kts") }
            .map { it.absolutePath }
            .toList()

    val args = K2JVMCompilerArguments().apply {
        freeArgs = sourceFiles
        this.classpath = classpath.joinToString(File.pathSeparator) { it.absolutePath }
        destination = outputDirectory.absolutePath
        jdkHome = jrePath.absolutePath
        moduleName = "KotlinCompilerWrapperModule"
        noStdlib = true
        reportOutputFiles = true
        languageVersion = "2.3"
        apiVersion = "2.3"
        jvmTarget = "21"
        pluginOptions = arrayOf("plugin:kotlin.scripting:script-templates=${livePluginScriptClass.name}")
        allowAnyScriptsInSourceRoots = true
        useFirLT = false
        pluginClasspaths = classpath.filter { it.name.contains("scripting-compiler") }.map { it.absolutePath }.toTypedArray()
    }

    val messageCollector = ErrorMessageCollector()
    val exitCode = K2JVMCompiler().exec(messageCollector, Services.EMPTY, args)

    return if (exitCode == ExitCode.OK) emptyList() else {
        messageCollector.errStream.toString()
            .ifEmpty { "Compiler finished with exit code $exitCode but no errors were reported." }
            .lines()
    }
}

private class ErrorMessageCollector(
    val errStream: ByteArrayOutputStream = ByteArrayOutputStream(),
    private val messageRenderer: MessageRenderer = PLAIN_FULL_PATHS
) : MessageCollector {
    private var hasErrors = false

    override fun report(
        severity: CompilerMessageSeverity,
        message: String,
        location: CompilerMessageSourceLocation?
    ) {
        if (severity in CompilerMessageSeverity.VERBOSE) return
        if (severity.isError) {
            val line = messageRenderer.render(severity, message, location) + "\n"
            errStream.write(line.toByteArray(UTF_8))
            hasErrors = true
        }
    }

    override fun clear() {}

    override fun hasErrors() = hasErrors
}
