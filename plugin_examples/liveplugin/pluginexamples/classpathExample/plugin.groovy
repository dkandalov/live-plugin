import static liveplugin.PluginUtil.*
import org.mockito.Mockito

// add-to-classpath $HOME/.m2/repository/org/mockito/mockito-all/1.8.4/mockito-all-1.8.4.jar

// The line above loads 'mockito-all' jar from the specified path.
// Line must start with "// add-to-classpath " followed by path.
//
// Any environment variable can be used with '$' as a prefix (e.g. "$ENV_VARIABLE").
// This is to avoid hardcoded paths, e.g. to maven repository.
//
// (Note that new classloader is created on each plugin evaluation.)
show(Mockito.class.name)

