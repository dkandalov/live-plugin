package liveplugin.implementation

class ShellCommands {
	static Map execute(String fullCommand) {
		fullCommand.split(" ").toList().with { execute(it.head(), it.tail()) }
	}

	static Map execute(String command, String parameters) {
		execute(command, parameters.split(" ").toList())
	}

	static Map execute(String command, Collection<String> parameters) {
		def ant = new AntBuilder()
		ant.exec(outputproperty:"cmdOut",
				errorproperty: "cmdErr",
				resultproperty:"cmdExit",
				failonerror: "false",
				executable: command) {
			arg(line: parameters.join(" "))
		}

		[exitCode: Integer.parseInt(ant.project.properties.cmdExit),
		 stderr: ant.project.properties.cmdErr,
		 stdout: ant.project.properties.cmdOut]
	}
}
