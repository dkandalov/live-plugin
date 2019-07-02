package liveplugin.pluginrunner

import com.intellij.openapi.util.io.FileUtil
import kotlin.Unit
import kotlin.jvm.functions.Function0
import kotlin.jvm.functions.Function1
import liveplugin.pluginrunner.groovy.GroovyPluginRunner
import org.junit.After
import org.junit.Before
import org.junit.Test

import static liveplugin.pluginrunner.AnError.RunningError
import static liveplugin.pluginrunner.Result.Failure
import static liveplugin.pluginrunner.Result.Success
import static liveplugin.pluginrunner.groovy.GroovyPluginRunner.mainScript

class GroovyPluginRunnerTest {
	static final LinkedHashMap noBindings = [:]
	static final LinkedHashMap emptyEnvironment = [:]
	static final Function1 runOnTheSameThread = new Function1<Function0<Result>, Result>() {
		@Override Result invoke(Function0<Result> f) { f.invoke() }
	}
	private final GroovyPluginRunner pluginRunner = new GroovyPluginRunner(mainScript, emptyEnvironment)
	private File rootFolder
	private File myPackageFolder


	@Test void "run correct groovy script without errors"() {
		def scriptCode = """
			// import to ensure that script has access to parent classloader from which test is run
			import com.intellij.openapi.util.io.FileUtil

			// some groovy code
			def a = 1
			def b = 2
			a + b
		"""
		createFile("plugin.groovy", scriptCode, rootFolder)
		def result = runPlugin()

		assert result instanceof Success
	}

	@Test void "run incorrect groovy script with errors"() {
		def scriptCode = """
			invalid code + 1
		"""
		createFile("plugin.groovy", scriptCode, rootFolder)
		def result = runPlugin()

		assert result instanceof Failure
		assert (result.reason as RunningError).pluginId == rootFolder.name
		assert (result.reason as RunningError).throwable.toString().startsWith("groovy.lang.MissingPropertyException")
	}

	@Test void "run groovy script which uses groovy class from another file"() {
		def scriptCode = """
			import myPackage.Util
			Util.myFunction()
		"""
		def scriptCode2 = """
			package myPackage
			class Util {
				static myFunction() { 42 }
			}
		"""
		createFile("plugin.groovy", scriptCode, rootFolder)
		createFile("Util.groovy", scriptCode2, myPackageFolder)

		def result = runPlugin()

		assert result instanceof Success
	}

	@Before void setup() {
		rootFolder = FileUtil.createTempDirectory("", "")
		myPackageFolder = new File(rootFolder, "myPackage")
		myPackageFolder.mkdir()
	}

	@After void teardown() {
		FileUtil.delete(rootFolder)
	}

	private Result<Unit, AnError> runPlugin() {
		pluginRunner.runPlugin(new LivePlugin(rootFolder.absolutePath), noBindings, runOnTheSameThread)
	}

	static createFile(String fileName, String fileContent, File directory) {
		def file = new File(directory, fileName)
		file.write(fileContent)
		file
	}
}
