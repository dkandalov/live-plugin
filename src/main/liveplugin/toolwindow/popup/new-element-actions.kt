package liveplugin.toolwindow.popup

import com.intellij.openapi.fileChooser.actions.NewFolderAction
import liveplugin.Icons
import liveplugin.IdeUtil
import liveplugin.LivePluginPaths.groovyExamplesPath
import liveplugin.LivePluginPaths.kotlinExamplesPath
import liveplugin.pluginrunner.groovy.GroovyPluginRunner
import liveplugin.toolwindow.util.readSampleScriptFile

class NewDirectoryAction: NewFolderAction("Directory", "", Icons.newFolderIcon)

class NewTextFileAction: NewFileAction("Text File", IdeUtil.textFileType)

class NewGroovyFileAction: NewFileAction("Groovy File", IdeUtil.groovyFileType)

class NewKotlinFileAction: NewFileAction("Kotlin File", IdeUtil.kotlinFileType)

class NewGroovyMainScript: NewFileFromTemplateAction(
    GroovyPluginRunner.mainScript,
    GroovyPluginRunner.mainScript,
    readSampleScriptFile("$groovyExamplesPath/default-plugin.groovy"),
    IdeUtil.groovyFileType
)

class NewGroovyTestScript: NewFileFromTemplateAction(
    GroovyPluginRunner.testScript,
    GroovyPluginRunner.testScript,
    readSampleScriptFile("$groovyExamplesPath/default-plugin-test.groovy"),
    IdeUtil.groovyFileType
)

class NewPluginXmlScript(
    fileContent: String = readSampleScriptFile("$kotlinExamplesPath/plugin.xml")
): NewFileFromTemplateAction(
    "plugin.xml",
    "plugin.xml",
    fileContent,
    IdeUtil.xmlFileType
)
