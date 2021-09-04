### Building the plugin
Use gradle tasks, e.g. `./gradlew clean check buildPlugin`, where `clean` and `check` are built-in gradle tasks, 
and `buildPlugin` is provided by [gradle plugin for building IJ plugins](https://github.com/JetBrains/gradle-intellij-plugin).

### Running the plugin
Use gradle `runIde` task provided by [gradle IJ plugin](https://github.com/JetBrains/gradle-intellij-plugin).
It will download IDE jars for the specified version and will start a new instance of IDE with the plugin.
To specify IDE version use `IJ_VERSION` env variable or modify `build.gradle` file.

### Understanding the code
Just like with any other IntelliJ plugin the best place to start is probably `plugin.xml`.
Packages are structured in an attempt to reflect toolwindow UI of the plugin.
But overall, this is an experimental proof-of-concept project, and I never managed to refactor it to a good state.

