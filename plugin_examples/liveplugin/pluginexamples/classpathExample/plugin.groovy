import static liveplugin.PluginUtil.*
import org.mockito.Mockito

// add-to-classpath $HOME/.m2/repository/org/mockito/mockito-all/1.8.4/mockito*.jar

// The line above adds to classloader all mockito jars from the specified path.
//
// Line must start with "// add-to-classpath " followed by path.
// Any environment variable can be used with '$' as a prefix (e.g. "$ENV_VARIABLE").
// Glob wildcards can be used in file name.
//
// (Note that new classloader is created on each plugin evaluation.)
show(Mockito.class.name)

