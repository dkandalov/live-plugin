import liveplugin.*

// add-to-classpath $HOME/.m2/repository/org/mockito/mockito-core/3.3.3/mockito*.jar
// depends-on-plugin Git4Idea

if (!isIdeStartup) {
    show(org.mockito.Mockito::class)
    show(git4idea.GitUtil::class)
}

// You can add jars or folder with class files to plugin classloader:
// - After "import" statements insert "// add-to-classpath " followed by a path.
// - Path must have "/" separators and can include glob wildcards if you want to include multiple jars.
// - Use '$' as prefix (e.g. "$JAVA_HOME") to refer to OS environment variables
//   (additional $PLUGIN_PATH variable contains path to the current plugin folder).

// You can use classes from other IDE plugins by specifying dependency on that plugin:
// - After "import" statements insert "// depends-on-plugin " followed by plugin id
//   which can be found in the plugin's "META-INF/plugin.xml".

// Note that:
// - "plugin.kts" files have intentions to insert "// add-to-classpath" and "// depends-on-plugin"
//   (use alt+Enter to see popup with intentions)
// - New classloader is created each time plugin is executed.
//   It means that addition classpath library classes are loaded on each plugin execution and
//   won't be unloaded if there are objects referencing them or classloader.
//   See also https://docs.oracle.com/javase/specs/jls/se7/html/jls-12.html#jls-12.7
