package liveplugin.toolwindow.settingsmenu

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.roots.OrderRootType.CLASSES
import com.intellij.openapi.roots.OrderRootType.SOURCES
import com.intellij.openapi.util.Pair.pair
import com.intellij.util.PathUtil.getJarPathForClass
import liveplugin.LivePluginAppComponent
import liveplugin.LivePluginAppComponent.ideJarsPath
import liveplugin.LivePluginAppComponent.livepluginLibsPath
import liveplugin.toolwindow.util.DependenciesUtil.*
import java.io.File

class AddLivePluginAndIdeJarsAsDependencies: AnAction(), DumbAware {
    private val livePluginAndIdeJarsLibrary = "LivePlugin and IDE jars (to enable navigation and auto-complete)"
    private val ideaJarsLibrary_Old = "IDEA jars" // TODO remove
    private val livePluginLibrary_Old = "LivePlugin" // TODO remove

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return

        if (anyModuleHasLibraryAsDependencyIn(project, livePluginLibrary_Old)) {
            removeLibraryDependencyFrom(project, livePluginLibrary_Old)
        }
        if (anyModuleHasLibraryAsDependencyIn(project, ideaJarsLibrary_Old)) {
            removeLibraryDependencyFrom(project, ideaJarsLibrary_Old)
        }

        if (anyModuleHasLibraryAsDependencyIn(project, livePluginAndIdeJarsLibrary)) {
            removeLibraryDependencyFrom(project, livePluginAndIdeJarsLibrary)
        } else {
            val livePluginEntries =
                File(livepluginLibsPath).listFiles().filter { it.name.endsWith(".jar") }.map { pair("jar://${it.absolutePath}!/", CLASSES) } +
                    pair("jar://" + getJarPathForClass(LivePluginAppComponent::class.java) + "!/", SOURCES)

            val ideJars = File(ideJarsPath).listFiles().toList().filter { it.name.endsWith(".jar") }
                .map { pair("jar://${it.absolutePath}!/", CLASSES) }

            addLibraryDependencyTo(project, livePluginAndIdeJarsLibrary, livePluginEntries + ideJars)
        }
    }

    override fun update(event: AnActionEvent) {
        val project = event.project ?: return

        if (anyModuleHasLibraryAsDependencyIn(project, livePluginAndIdeJarsLibrary)) {
            event.presentation.text = "Remove LivePlugin and IDE Jars from Project"
            event.presentation.description =
                "Remove LivePlugin and IDE Jars from project dependencies. " +
                "This will enable auto-complete and other IDE features for IntelliJ classes."
        } else {
            event.presentation.text = "Add LivePlugin and IDE Jars to Project"
            event.presentation.description =
                "Add LivePlugin and IDE jars to project dependencies. " +
                "This will enable auto-complete and other IDE features."
        }
    }
}
