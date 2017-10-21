package liveplugin.toolwindow.popup

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileChooser.FileSystemTree
import liveplugin.IDEUtil
import liveplugin.LivePluginAppComponent
import liveplugin.LivePluginAppComponent.defaultPluginScript
import liveplugin.LivePluginAppComponent.defaultPluginTestScript
import liveplugin.pluginrunner.GroovyPluginRunner

class NewTextFileAction: NewFileAction("Text File", AllIcons.FileTypes.Text)

class NewGroovyFileAction: NewFileAction("Groovy File", IDEUtil.groovyFileType)

class NewKotlinFileAction: NewFileAction("Kotlin File", IDEUtil.kotlinFileType)

class NewGroovyMainScript: NewFileFromTemplateAction(
    GroovyPluginRunner.mainScript,
    GroovyPluginRunner.mainScript,
    defaultPluginScript(),
    NewElementPopupAction.inferIconFromFileType,
    IDEUtil.groovyFileType
)

class NewGroovyTestScript: NewFileFromTemplateAction(
    GroovyPluginRunner.testScript,
    GroovyPluginRunner.testScript,
    defaultPluginTestScript(),
    NewElementPopupAction.inferIconFromFileType,
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
