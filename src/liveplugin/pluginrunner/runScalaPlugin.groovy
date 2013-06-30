package liveplugin.pluginrunner
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.PathUtil
import liveplugin.MyFileUtil
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.IMain
import scala.tools.nsc.settings.MutableSettings

import static liveplugin.PluginUtil.changeGlobalVar
import static liveplugin.PluginUtil.showInConsole

def (IMain interpreter, StringWriter output) = changeGlobalVar("scalaInterpreter") { prevValue ->
	if (prevValue != null) return prevValue

	MutableSettings settings = new Settings()
	def bootClasspath = (MutableSettings.PathSetting) settings.bootclasspath()

	def compilerPath = PathUtil.getJarPathForClass(Class.forName("scala.tools.nsc.Interpreter"))
	def scalaLibPath = PathUtil.getJarPathForClass(Class.forName("scala.Some"))
	def intellijLibPath = new File(PathManager.libPath).listFiles().collect{it.absolutePath}
	def intellijPluginsPath = new File(PathManager.pluginsPath).listFiles().collect{it.absolutePath}
	def livePluginPath = PathManager.getResourceRoot(ScalaPluginRunner.class, "/liveplugin/") // this is only useful when running liveplugin locally (it's not packed into jar)
	def path = ([bootClasspath.value(), compilerPath, scalaLibPath, livePluginPath] + intellijLibPath + intellijPluginsPath).join(File.pathSeparator)

	bootClasspath.append(path)
	((MutableSettings.BooleanSetting)settings.usejavacp()).tryToSetFromPropertyValue("true")

	def output = new StringWriter()
	def interpreter = new IMain(settings, new PrintWriter(output))
	[interpreter, output]
}

def scriptFile = MyFileUtil.findSingleFileIn(pathToPluginFolder, ScalaPluginRunner.MAIN_SCRIPT)
assert scriptFile != null

output.buffer.delete(0, output.buffer.size())

interpreter.bindValue("event", event)
interpreter.bindValue("project", project)
interpreter.bindValue("isIdeStartup", isIdeStartup)
interpreter.bindValue("pluginPath", pluginPath)
def result = interpreter.interpret(FileUtil.loadFile(scriptFile))

if (!result.class.name.endsWith("Results\$Success\$")) { // check as string because equality with Results.Success doesn't work in groovy
	showInConsole(output.toString(), "", project, ConsoleViewContentType.ERROR_OUTPUT)
}