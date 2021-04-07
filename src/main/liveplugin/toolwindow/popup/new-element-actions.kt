package liveplugin.toolwindow.popup

import com.intellij.openapi.fileChooser.actions.NewFolderAction
import liveplugin.Icons
import liveplugin.IdeUtil
import liveplugin.LivePluginAppComponent.Companion.readSampleScriptFile
import liveplugin.LivePluginPaths.groovyExamplesPath
import liveplugin.LivePluginPaths.kotlinExamplesPath
import liveplugin.pluginrunner.groovy.GroovyPluginRunner
import javax.swing.Icon

class NewDirectoryAction: NewFolderAction("Directory", "", Icons.newFolderIcon)

class NewTextFileAction: NewFileAction("Text File", IdeUtil.textFileType)

class NewGroovyFileAction: NewFileAction("Groovy File", IdeUtil.groovyFileType)

class NewKotlinFileAction: NewFileAction("Kotlin File", IdeUtil.kotlinFileType)

class NewGroovyMainScript: NewFileFromTemplateAction(
    GroovyPluginRunner.mainScript,
    GroovyPluginRunner.mainScript,
    readSampleScriptFile("$groovyExamplesPath/default-plugin.groovy"),
    inferIconFromFileType,
    IdeUtil.groovyFileType
)

class NewGroovyTestScript: NewFileFromTemplateAction(
    GroovyPluginRunner.testScript,
    GroovyPluginRunner.testScript,
    readSampleScriptFile("$groovyExamplesPath/default-plugin-test.groovy"),
    inferIconFromFileType,
    IdeUtil.groovyFileType
)

class NewPluginXmlScript: NewFileFromTemplateAction(
    "plugin.xml",
    "plugin.xml",
    readSampleScriptFile("$kotlinExamplesPath/plugin.xml"),
    inferIconFromFileType,
    IdeUtil.xmlFileType
)

private val inferIconFromFileType: Icon? = null