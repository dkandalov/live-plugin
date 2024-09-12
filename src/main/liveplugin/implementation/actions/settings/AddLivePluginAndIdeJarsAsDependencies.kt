package liveplugin.implementation.actions.settings

import com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope.PROVIDED
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.OrderRootType.CLASSES
import com.intellij.openapi.roots.OrderRootType.SOURCES
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.util.PathUtil
import liveplugin.implementation.LivePlugin
import liveplugin.implementation.LivePluginPaths.ideJarsPath
import liveplugin.implementation.LivePluginPaths.livePluginLibPath

class AddLivePluginAndIdeJarsAsDependencies: AnAction(), DumbAware {
    private val livePluginAndIdeJarsLibrary = "LivePlugin and IDE jars (to enable navigation and auto-complete)"

    private val projectLibrariesNames = ProjectLibrariesNames()

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return

        if (projectLibrariesNames.contains(project, livePluginAndIdeJarsLibrary)) {
            removeLibraryDependencyFrom(project, livePluginAndIdeJarsLibrary)
        } else {
            val livePluginSrc = Pair("jar://" + PathUtil.getJarPathForClass(LivePlugin::class.java) + "!/", SOURCES)

            val livePluginJars = livePluginLibPath
                .listFiles { it.name.endsWith(".jar") }
                .map { Pair("jar://${it.value}!/", CLASSES) }

            val ideJars = ideJarsPath
                .listFiles { it.name.endsWith(".jar") }
                .map { Pair("jar://${it.value}!/", CLASSES) }

            addLibraryDependencyTo(project, livePluginAndIdeJarsLibrary, livePluginJars + livePluginSrc + ideJars)
        }
    }

    override fun update(event: AnActionEvent) {
        val project = event.project ?: return

        if (projectLibrariesNames.contains(project, livePluginAndIdeJarsLibrary)) {
            event.presentation.text = "Remove LivePlugin and IDE Jars from Project"
            event.presentation.description = "Remove LivePlugin and IDE Jars from project dependencies."
        } else {
            event.presentation.text = "Add LivePlugin and IDE Jars to Project"
            event.presentation.description = "Add LivePlugin and IDE jars to project dependencies. " +
                "This will enable auto-complete and other IDE features for Groovy live-plugins."
        }
    }

    override fun getActionUpdateThread() = BGT

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

private fun removeLibraryDependencyFrom(project: Project, libraryName: String) {
    runWriteAction {
        ModuleManager.getInstance(project).modules.forEach { module ->
            if (module.dependsOn(libraryName)) {
                module.removeDependencyOn(libraryName)
            }
        }
    }
}

private fun addLibraryDependencyTo(project: Project, libraryName: String, paths: List<Pair<String, OrderRootType>>) {
    runWriteAction {
        val modules = ModuleManager.getInstance(project).modules
        if (modules.isNotEmpty()) {
            // Add dependency to the first module because this is enough for IntelliJ to see classes
            // and adding dependency to all modules can be very slow for large projects
            // (~16 seconds with UI freeze for IntelliJ source code).
            modules[0].addDependencyOn(libraryName, paths)
        }
    }
}

private fun Module.removeDependencyOn(libraryName: String) {
    val modifiableModel = ModuleRootManager.getInstance(this).modifiableModel
    val libraryTable = modifiableModel.moduleLibraryTable

    val library = libraryTable.getLibraryByName(libraryName)
    if (library != null) libraryTable.removeLibrary(library)

    modifiableModel.commit()
}

private fun Module.addDependencyOn(libraryName: String, paths: List<Pair<String, OrderRootType>>) {
    val modifiableModel = ModuleRootManager.getInstance(this).modifiableModel
    val libraryTable = modifiableModel.moduleLibraryTable
    if (libraryTable.contains(libraryName)) return

    val library = libraryTable.createLibrary(libraryName)
    library.modifiableModel.let {
        paths.forEach { (path, rootType) -> it.addRoot(path, rootType) }
        it.commit()
    }

    val libraryOrderEntry = modifiableModel.findLibraryOrderEntry(library)
    if (libraryOrderEntry != null) libraryOrderEntry.scope = PROVIDED
    modifiableModel.commit()
}

private fun Module.dependsOn(libraryName: String): Boolean {
    val moduleRootManager = ModuleRootManager.getInstance(this).modifiableModel
    val library = moduleRootManager.moduleLibraryTable.getLibraryByName(libraryName)
    moduleRootManager.dispose()
    return library != null
}

private fun LibraryTable.contains(libraryName: String) = getLibraryByName(libraryName) != null
