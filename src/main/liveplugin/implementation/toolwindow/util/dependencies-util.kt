package liveplugin.implementation.toolwindow.util

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.DependencyScope.PROVIDED
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.LibraryTable

fun removeLibraryDependencyFrom(project: Project, libraryName: String) {
    runWriteAction {
        ModuleManager.getInstance(project).modules.forEach { module ->
            if (module.dependsOn(libraryName)) {
                module.removeDependencyOn(libraryName)
            }
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

fun addLibraryDependencyTo(project: Project, libraryName: String, paths: List<Pair<String, OrderRootType>>) {
    runWriteAction {
        val modules = ModuleManager.getInstance(project).modules
        if (modules.isNotEmpty()) {
            // Add dependency to the first module because this is enough for IntelliJ to see classes
            // and adding dependency to all modules can be very slow for large projects
            // (~16 seconds with UI freeze for IntelliJ source code).
            addLibraryDependencyTo(modules[0], libraryName, paths)
        }
    }
}

private fun addLibraryDependencyTo(module: Module, libraryName: String, paths: List<Pair<String, OrderRootType>>) {
    val modifiableModel = ModuleRootManager.getInstance(module).modifiableModel
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
