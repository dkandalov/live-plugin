### Compiling
- use "gradle downloadLibs", it will download some of dependencies into "lib" folder
- open project in IntelliJ IDEA
- configure project SDK to use "IntelliJ Platform Plugin SDK" for IJ 12
- add the following plugin libraries to IntelliJ SDK from Idea installation folder
    - plugins/github/lib/github.jar
    - plugins/git4idea/lib/git4idea-rt.jar
    - plugins/git4idea/lib/git4idea.jar
    - plugins/junit/lib/idea-junit.jar
    - plugins/junit/lib/junit-rt.jar
    - plugins/junit/lib/resources_en.jar
- if it still doesn't compile, feel free to create a bug on github


### Understanding the code

Just like for most plugins the best place to start is plugin.xml.
Packages are structured in attempt to reflect plugins toolwindow UI although not necessarily match it.

Please be aware this is a proof-of-concept project, don't expect to see great code.
I could write a blog post about extendable IDEs but probably not many people would read it
so this is kind of "blog with working code" approach.
