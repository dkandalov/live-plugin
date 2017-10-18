package liveplugin.toolwindow.settingsmenu

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType.CLASSES
import com.intellij.openapi.roots.OrderRootType.SOURCES
import com.intellij.openapi.util.Pair.pair
import com.intellij.util.PathUtil.getJarPathForClass
import liveplugin.LivePluginAppComponent
import liveplugin.LivePluginAppComponent.ideJarsPath
import liveplugin.LivePluginAppComponent.livepluginLibsPath
import liveplugin.toolwindow.util.DependenciesUtil.addLibraryDependencyTo
import liveplugin.toolwindow.util.DependenciesUtil.removeLibraryDependencyFrom
import java.io.File

class AddLivePluginAndIdeJarsAsDependencies: AnAction(), DumbAware {
    private val livePluginAndIdeJarsLibrary = "LivePlugin and IDE jars (to enable navigation and auto-complete)"
    private val ideaJarsLibrary_Old = "IDEA jars" // TODO remove
    private val livePluginLibrary_Old = "LivePlugin" // TODO remove

    private val projectLibrariesNames = ProjectLibrariesNames()

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return

        if (projectLibrariesNames.contains(project, livePluginLibrary_Old)) {
            removeLibraryDependencyFrom(project, livePluginLibrary_Old)
        }
        if (projectLibrariesNames.contains(project, ideaJarsLibrary_Old)) {
            removeLibraryDependencyFrom(project, ideaJarsLibrary_Old)
        }

        if (projectLibrariesNames.contains(project, livePluginAndIdeJarsLibrary)) {
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

        if (projectLibrariesNames.contains(project, livePluginAndIdeJarsLibrary)) {
            event.presentation.text = "Remove LivePlugin and IDE Jars from Project"
            event.presentation.description = "" +
                "Remove LivePlugin and IDE Jars from project dependencies. " +
                "This will enable auto-complete and other IDE features for IntelliJ classes."
        } else {
            event.presentation.text = "Add LivePlugin and IDE Jars to Project"
            event.presentation.description = "" +
                "Add LivePlugin and IDE jars to project dependencies. " +
                "This will enable auto-complete and other IDE features."
        }
    }

    private class ProjectLibrariesNames {
        private var modificationCount = -1L
        private var value = emptyList<String>()

        fun contains(project: Project, libraryName: String): Boolean {
            val moduleManager = ModuleManager.getInstance(project)
            if (moduleManager.modificationCount != modificationCount) {
                value = moduleManager.modules.flatMap { module ->
                    val moduleRootManager = ModuleRootManager.getInstance(module).modifiableModel
                    try {
                        moduleRootManager.moduleLibraryTable.libraries.mapNotNull { it.name }
                    } finally {
                        moduleRootManager.dispose()
                    }
                }
                modificationCount = moduleManager.modificationCount
            }
            return value.contains(libraryName)
        }
    }
}
