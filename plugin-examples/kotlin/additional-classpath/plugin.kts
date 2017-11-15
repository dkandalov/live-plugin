import org.mockito.Mockito

import liveplugin.*

// add-to-classpath $HOME/.m2/repository/org/mockito/mockito-all/1.9.5/mockito*.jar

if (!isIdeStartup) {
    show(Mockito::class.java.name)
}
