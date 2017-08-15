### Reporting an issue/question
 - in case of any problems with code or documentation don't hesitate to report them :)
 - if something doesn't work, code example might be useful 
 - if you get an exception, full stacktrace and IDE name/version can make it easier to reproduce the bug  


### Compiling
 - open project in IntelliJ
 - configure project JDK to use "IntelliJ Platform Plugin SDK" 
 - edit `build.gradle`
    - `ext.ideaPath` should point to the same IntelliJ SDK as in the previous step 
    - `pluginsSandbox` should point to the right location 
 - in "Gradle project" toolwindow refresh LivePlugin module (this will update module dependencies)
 - compile the project
 - (optional) close and open project if compilation fails with `java.lang.NoClassDefFoundError: org/apache/tools/ant/util/ReaderInputStream`, 
 - (optional) to regenerate module file from gradle build run `idea` gradle task 
 - (optional) if you don't have internet access, add dependencies from `lib` folder
    
 
### Running plugin
 - edit `build.gradle` so that `ext.pluginsSandbox` points to correct plugins sandbox 
   (on Linux/Windows it's located in `.IntelliJ` folder) 
 - use `LivePlugin` configuration to run project
   (note that before running this configuration executes `copyResources` gradle task to copy resources; 
   this is because for some reason IntelliJ doesn't reliably copy resources with current project layout)


### Building as zip/jar
 - use `Build -> Build Artifacts`
    - `LivePlugin.zip` can be installed/distributed as a plugin
    - `LivePlugin.jar` can be used as a library in other plugins (this is still experimental)  


### Understanding the code
Just like with other IntelliJ plugins the best place to start is probably plugin.xml.
Packages are structured in attempt to reflect plugins toolwindow UI.

Please be aware this is a proof-of-concept project, don't expect to see great code.
I could write a blog post about extendable IDEs but probably not many people would read it
so this is "blog with working code" approach (<-- pathetic excuse for creating a mess).
