package liveplugin.implementation

class ShellCommands {
	static Map execute(String fullCommand) {
		fullCommand.split(" ").toList().with { execute(it.head(), it.tail()) }
	}

	static Map execute(String command, String parameters) {
		execute(command, parameters.split(" ").toList())
	}

	static Map execute(String command, Collection<String> parameters) {
		def process = new ProcessBuilder(command, *parameters.toArray()).start()
		def stdout = process.inputStream.text
		def stderr = process.errorStream.text
		[exitCode: process.exitValue(), stdout: stdout, stderr: stderr]
	}
}
