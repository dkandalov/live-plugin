package liveplugin.toolwindow.popup

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileSystemTree
import com.intellij.openapi.fileChooser.actions.NewFolderAction
import liveplugin.IdeUtil
import liveplugin.Icons
import liveplugin.LivePluginAppComponent
import liveplugin.LivePluginAppComponent.Companion.defaultPluginScript
import liveplugin.LivePluginAppComponent.Companion.defaultPluginTestScript
import liveplugin.pluginrunner.GroovyPluginRunner
import javax.swing.Icon

class NewTextFileAction: NewFileAction("Text File", AllIcons.FileTypes.Text)

class NewDirectoryAction: NewFolderAction("Directory", "", Icons.newFolderIcon)

class NewGroovyFileAction: NewFileAction("Groovy File", IdeUtil.groovyFileType)

class NewKotlinFileAction: NewFileAction("Kotlin File", IdeUtil.kotlinFileType)

class NewGroovyMainScript: NewFileFromTemplateAction(
    GroovyPluginRunner.mainScript,
    GroovyPluginRunner.mainScript,
    defaultPluginScript(),
    inferIconFromFileType,
    IdeUtil.groovyFileType
)

class NewGroovyTestScript: NewFileFromTemplateAction(
    GroovyPluginRunner.testScript,
    GroovyPluginRunner.testScript,
    defaultPluginTestScript(),
    inferIconFromFileType,
    IdeUtil.groovyFileType
)

class NewClojureFileAction: NewFileAction("Clojure File", IdeUtil.clojureFileType) {
    override fun update(fileSystemTree: FileSystemTree, e: AnActionEvent) {
        super.update(fileSystemTree, e)
        e.presentation.isVisible = LivePluginAppComponent.clojureIsOnClassPath()
    }
}

class NewScalaFileAction: NewFileAction("Scala File", IdeUtil.scalaFileType) {
    override fun update(fileSystemTree: FileSystemTree, e: AnActionEvent) {
        super.update(fileSystemTree, e)
        e.presentation.isVisible = LivePluginAppComponent.scalaIsOnClassPath()
    }
}

private val inferIconFromFileType: Icon? = null