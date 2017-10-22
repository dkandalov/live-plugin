package liveplugin.toolwindow.settingsmenu.languages

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.roots.OrderRootType.CLASSES
import com.intellij.openapi.util.Pair.create
import com.intellij.util.containers.ContainerUtil.map
import liveplugin.LivePluginAppComponent.Companion.clojureIsOnClassPath
import liveplugin.LivePluginAppComponent.Companion.livepluginLibsPath
import liveplugin.MyFileUtil.fileNamesMatching
import liveplugin.toolwindow.util.DependenciesUtil

class AddClojureLibsAsDependency: AnAction(), DumbAware {

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return

        if (DependenciesUtil.anyModuleHasLibraryAsDependencyIn(project, libraryName)) {
            DependenciesUtil.removeLibraryDependencyFrom(project, libraryName)
        } else {
            val paths = map(fileNamesMatching(DownloadClojureLibs.libFilesPattern, livepluginLibsPath)) { fileName -> create("jar://$livepluginLibsPath$fileName!/", CLASSES) }
            DependenciesUtil.addLibraryDependencyTo(project, libraryName, paths)
        }
    }

    override fun update(event: AnActionEvent) {
        val project = event.project ?: return

        if (DependenciesUtil.anyModuleHasLibraryAsDependencyIn(project, libraryName)) {
            event.presentation.text = "Remove Clojure Libraries from Project"
            event.presentation.description = "Remove Clojure Libraries from Project"
        } else {
            event.presentation.text = "Add Clojure Libraries to Project"
            event.presentation.description = "Add Clojure Libraries to Project"
            event.presentation.isEnabled = clojureIsOnClassPath()
        }
    }

    companion object {
        private val libraryName = "LivePlugin - Clojure"
    }
}
