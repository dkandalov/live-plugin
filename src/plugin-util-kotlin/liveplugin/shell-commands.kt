package liveplugin

import java.io.File

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

fun runShellCommand(command: String, vararg arguments: String): CommandResult {
    val process = ProcessBuilder(command, *arguments).start()
    val stdout = process.inputStream.bufferedReader().readText()
    val stderr = process.errorStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    return CommandResult(exitCode, stdout, stderr)
}

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)