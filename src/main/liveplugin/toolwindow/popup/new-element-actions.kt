package liveplugin.toolwindow.popup

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileSystemTree
import com.intellij.openapi.fileChooser.actions.NewFolderAction
import liveplugin.IDEUtil
import liveplugin.Icons
import liveplugin.LivePluginAppComponent
import liveplugin.LivePluginAppComponent.defaultPluginScript
import liveplugin.LivePluginAppComponent.defaultPluginTestScript
import liveplugin.pluginrunner.GroovyPluginRunner
import javax.swing.Icon

class NewTextFileAction: NewFileAction("Text File", AllIcons.FileTypes.Text)

class NewDirectoryAction: NewFolderAction("Directory", "", Icons.newFolderIcon)

class NewGroovyFileAction: NewFileAction("Groovy File", IDEUtil.groovyFileType)

class NewKotlinFileAction: NewFileAction("Kotlin File", IDEUtil.kotlinFileType)

class NewGroovyMainScript: NewFileFromTemplateAction(
    GroovyPluginRunner.mainScript,
    GroovyPluginRunner.mainScript,
    defaultPluginScript(),
    inferIconFromFileType,
    IDEUtil.groovyFileType
)

class NewGroovyTestScript: NewFileFromTemplateAction(
    GroovyPluginRunner.testScript,
    GroovyPluginRunner.testScript,
    defaultPluginTestScript(),
    inferIconFromFileType,
    IDEUtil.groovyFileType
)

class NewClojureFileAction: NewFileAction("Clojure File", IDEUtil.clojureFileType) {
    override fun update(fileSystemTree: FileSystemTree, e: AnActionEvent) {
        super.update(fileSystemTree, e)
        e.presentation.isVisible = LivePluginAppComponent.clojureIsOnClassPath()
    }
}

class NewScalaFileAction: NewFileAction("Scala File", IDEUtil.scalaFileType) {
    override fun update(fileSystemTree: FileSystemTree, e: AnActionEvent) {
        super.update(fileSystemTree, e)
        e.presentation.isVisible = LivePluginAppComponent.scalaIsOnClassPath()
    }
}

private val inferIconFromFileType: Icon? = null