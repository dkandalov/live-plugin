@file:Suppress("unused")

package liveplugin

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.GeneralCommandLine.ParentEnvironmentType.CONSOLE
import java.io.File
import java.util.concurrent.CompletableFuture

fun runShellScript(script: String): CommandResult {
    val tempFile = File.createTempFile("LivePlugin-shell-script-", "").also {
        it.writeText(script)
        it.setExecutable(true)
    }
    return try {
        runShellCommand(tempFile.absolutePath)
    } finally {
        tempFile.delete()
    }
}

fun runShellCommand(vararg command: String): CommandResult {
    val process = GeneralCommandLine(command.toList())
        .withWorkDirectory(System.getProperty("user.home"))
        .withParentEnvironmentType(CONSOLE)
        .createProcess()
    val stdout = CompletableFuture.supplyAsync { process.inputStream.bufferedReader().readText() }
    val stderr = CompletableFuture.supplyAsync { process.errorStream.bufferedReader().readText() }
    val exitCode = process.waitFor()
    return CommandResult(exitCode, stdout.get(), stderr.get())
}

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)