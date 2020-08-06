import liveplugin.*

// add-to-classpath $HOME/.m2/repository/org/mockito/mockito-core/3.3.3/mockito*.jar

if (!isIdeStartup) {
    show(org.mockito.Mockito.Mockito::class.java.name)
}
