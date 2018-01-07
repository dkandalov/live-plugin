### Building the plugin
Use gradle tasks. 
E.g. `./gradlew clean check buildPlugin`, where `clean` and `check` are built-in gradle tasks, 
and `buildPlugin` is provided by [gradle plugin for building IJ plugins](https://github.com/JetBrains/gradle-intellij-plugin).

### Running the plugin
Use gradle `runIde` task provided by [gradle IJ plugin](https://github.com/JetBrains/gradle-intellij-plugin).
It will download IDE jars for the specified version and will start new instance of IDE with the plugin.
To specify IDE version use `LIVEPLUGIN_IDEA_VERSION` env variable or modify `build.gradle` file.

Note that gradle tasks can be configured as "Run configuration" so you can run use them directly from IDE.

### Understanding the code
Just like with other IntelliJ plugins the best place to start is probably plugin.xml.
Packages are structured in attempt to reflect plugins toolwindow UI.

Please be aware this is a proof-of-concept project, don't expect to see great code.
I could write a blog post about extendable IDEs but probably not many people would read it
so this is "blog with working code" approach (<-- pathetic excuse for creating a mess).
