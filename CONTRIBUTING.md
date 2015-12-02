### Compiling
 - open project in IntelliJ
 - configure project JDK to use "IntelliJ Platform Plugin SDK" for IJ 15 
 - edit ```build.gradle``` so that ```ext.ideaPath``` points to the same IntelliJ SDK as in previous step 
 - in "Gradle project" toolwindow refresh LivePlugin module (this will update module dependencies)
 - compile the project
 - (optional) if compilation fails with ```java.lang.NoClassDefFoundError: org/apache/tools/ant/util/ReaderInputStream```, 
   close/open project
 - (optional) to regenerate module file from gradle build run ```idea``` gradle task 
 - (optional) if you don't have internet access, add dependencies from ```lib``` folder
    
 
### Running plugin
 - edit ```build.gradle``` so that ```ext.pluginsSandbox``` points to correct plugins sandbox 
   (on Linux/Windows it's located in ```.IntelliJ``` folder) 
 - use ```LivePlugin``` run configuration to run project
   (note that it runs ```copyResources``` gradle task to copy resources, for some reason 
    IntelliJ doesn't reliably copy resources with current project layout)


### Building as zip/jar
 - use Build -> Build Artifacts
    - ```LivePlugin.zip``` can be installed/distributed as a plugin
    - ```LivePlugin.jar``` can be used as a library in other plugins (this is still experimental)  


### Understanding the code
Just like for most plugins the best place to start is plugin.xml.
Packages are structured in attempt to reflect plugins toolwindow UI although not necessarily match it.

Please be aware this is a proof-of-concept project, don't expect to see great code.
I could write a blog post about extendable IDEs but probably not many people would read it
so this is "blog with working code" approach.
