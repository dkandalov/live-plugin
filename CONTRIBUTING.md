### Setup
 - open project in IntelliJ
 - configure project JDK to use "IntelliJ Platform Plugin SDK" for IJ 14 
 - edit build.gradle so that "ext.ideaPath" points to the same IntelliJ SDK as in previous step 
 - in "Gradle project" toolwindow refresh LivePlugin module (it will update module dependencies)
 - (optional) to regenerate module file from gradle build run "idea" gradle command 
 - (optional) if you don't have internet access, add dependencies from "lib" folder
 - try compiling project
 - use "LivePlugin" run configuration to run project
 - if you have any problems, feel free to create a bug on github


### Building as zip/jar
 - use Build -> Build Artifacts
    - "LivePlugin.zip" can be installed/distributed as a plugin
    - "LivePlugin.jar" can be used as a library in other plugins (this is still experimental)  


### Understanding the code
Just like for most plugins the best place to start is plugin.xml.
Packages are structured in attempt to reflect plugins toolwindow UI although not necessarily match it.

Please be aware this is a proof-of-concept project, don't expect to see great code.
I could write a blog post about extendable IDEs but probably not many people would read it
so this is kind of "blog with working code" approach.
