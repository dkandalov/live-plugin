import static ru.intellijeval.PluginUtil.*
import org.mockito.Mockito

// add-to-classpath $HOME/.m2/repository/org/mockito/mockito-all/1.8.4/mockito-all-1.8.4.jar

// The line above makes plugin classloader to load mockito-all jar from the specified path.
// Any environment variable can be used with $ as a prefix, e.g. "$ENV_VARIABLE".

show(Mockito.class.name)
