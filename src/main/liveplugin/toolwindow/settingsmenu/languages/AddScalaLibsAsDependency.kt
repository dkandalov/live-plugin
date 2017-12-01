package liveplugin.toolwindow.settingsmenu.languages

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.roots.OrderRootType.CLASSES
import com.intellij.openapi.util.Pair.create
import com.intellij.util.containers.ContainerUtil.map
import liveplugin.LivePluginAppComponent.Companion.livePluginLibsPath
import liveplugin.LivePluginAppComponent.Companion.scalaIsOnClassPath
import liveplugin.MyFileUtil.fileNamesMatching
import liveplugin.toolwindow.util.DependenciesUtil

class AddScalaLibsAsDependency: AnAction(), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return

        if (DependenciesUtil.anyModuleHasLibraryAsDependencyIn(project, libraryName)) {
            DependenciesUtil.removeLibraryDependencyFrom(project, libraryName)
        } else {
            val paths = map(fileNamesMatching(DownloadScalaLibs.libFilesPattern, livePluginLibsPath)) { fileName -> create("jar://$livePluginLibsPath$fileName!/", CLASSES) }
            DependenciesUtil.addLibraryDependencyTo(project, libraryName, paths)
        }
    }

    override fun update(event: AnActionEvent) {
        val project = event.project ?: return

        if (DependenciesUtil.anyModuleHasLibraryAsDependencyIn(project, libraryName)) {
            event.presentation.text = "Remove Scala Libraries from Project"
            event.presentation.description = "Remove Scala Libraries from Project"
        } else {
            event.presentation.text = "Add Scala Libraries to Project"
            event.presentation.description = "Add Scala Libraries to Project"
            event.presentation.isEnabled = scalaIsOnClassPath()
        }
    }

    companion object {
        private val libraryName = "LivePlugin - Scala"
    }
}
