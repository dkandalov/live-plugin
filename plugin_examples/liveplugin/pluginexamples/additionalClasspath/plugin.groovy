import org.mockito.Mockito

import static liveplugin.PluginUtil.show

// add-to-classpath $HOME/.m2/repository/org/mockito/mockito-all/1.9.5/mockito*.jar
//
// The line above adds to classloader all mockito jars from the specified path.
//
// - Line must start with "// add-to-classpath " followed by path.
// - Any environment variable can be used with '$' as prefix (e.g. "$JAVA_HOME").
// - $PLUGIN_PATH variable points to current plugin folder.
// - Glob wildcards can be used in file name.
// - In plugin.groovy files there is intention to insert "add-to-classpath" comment
//   (use alt+Enter to see popup with intentions)
//
// (Note that new classloader is created each time plugin is executed.
// It means that classpath libraries are loaded on each execution and
// won't be GCed until there are no objects referencing classloader.)
show(Mockito.class.name)

