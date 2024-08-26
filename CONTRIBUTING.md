### Building the plugin
Use gradle tasks, e.g. `./gradlew clean check buildPlugin`, where `clean` and `check` are built-in gradle tasks, 
and `buildPlugin` is provided by [gradle plugin for building IJ plugins](https://github.com/JetBrains/gradle-intellij-plugin).

### Running the plugin
Use gradle `runIde` task provided by [gradle IJ plugin](https://github.com/JetBrains/gradle-intellij-plugin).
It will download IDE jars for the specified version and will start a new instance of IDE with the plugin.
To specify IDE version modify `build.gradle` file.
