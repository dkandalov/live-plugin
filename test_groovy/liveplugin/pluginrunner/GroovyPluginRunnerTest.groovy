package liveplugin.pluginrunner
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.ZipUtil
import org.junit.After
import org.junit.Before
import org.junit.Test

import java.util.zip.ZipOutputStream

import static liveplugin.MyFileUtil.asUrl

class GroovyPluginRunnerTest {
	private static final LinkedHashMap NO_BINDING = [:]
	private static final LinkedHashMap NO_ENVIRONMENT = [:]

	private final ErrorReporter errorReporter = new ErrorReporter()
	private final GroovyPluginRunner pluginRunner = new GroovyPluginRunner(errorReporter, NO_ENVIRONMENT)
	private File rootFolder
	private File myPackageFolder


	@Test void "should run correct groovy script without errors"() {
		def scriptCode = """
			// import to ensure that script has access to parent classloader from which test is run
			import com.intellij.openapi.util.io.FileUtil

			// some groovy code
			def a = 1
			def b = 2
			a + b
		"""
		def file = createFile("plugin.groovy", scriptCode, rootFolder)
		pluginRunner.runGroovyScript(asUrl(file), asUrl(rootFolder), "someId", NO_BINDING)

		assert collectErrorsFrom(errorReporter).empty
	}

	@Test void "should run incorrect groovy script with errors"() {
		def scriptCode = """
			invalid code + 1
		"""
		def file = createFile("plugin.groovy", scriptCode, rootFolder)
		pluginRunner.runGroovyScript(asUrl(file), asUrl(rootFolder), "someId", NO_BINDING)

		def errors = collectErrorsFrom(errorReporter)
		assert errors.size() == 1
		errors.first().with{
			assert it[0] == "someId"
			assert it[1].startsWith("groovy.lang.MissingPropertyException")
		}
	}

	@Test void "when script is in folder should run groovy script which uses groovy class from another file"() {
		def scriptCode = """
			import myPackage.Util
			Util.myFunction()
		"""
		def scriptCode2 = """
			class Util {
				static myFunction() { 42 }
			}
		"""
		def file = createFile("plugin.groovy", scriptCode, rootFolder)
		createFile("Util.groovy", scriptCode2, myPackageFolder)

		pluginRunner.runGroovyScript(asUrl(file), asUrl(rootFolder), "someId", NO_BINDING)

		assert collectErrorsFrom(errorReporter).empty
	}

	@Test void "when script is in jar file should run groovy script which uses groovy class from another file"() {
		def scriptCode = """
			import myPackage.Util
			Util.myFunction()
		"""
		def scriptCode2 = """
			class Util {
				static myFunction() { 42 }
			}
		"""
		createFile("plugin.groovy", scriptCode, rootFolder)
		createFile("Util.groovy", scriptCode2, myPackageFolder)

		givenJarred(rootFolder) { File jarFile ->
			def scriptUrl = "jar:file://" + jarFile.absolutePath + "!/plugin.groovy"
			def pluginFolderUrl = "file://" + rootFolder.absolutePath
			pluginRunner.runGroovyScript(scriptUrl, pluginFolderUrl, "someId", NO_BINDING)
			assert collectErrorsFrom(errorReporter).empty
		}
	}

	@Before void setup() {
		rootFolder = FileUtil.createTempDirectory("", "")
		myPackageFolder = new File(rootFolder, "myPackage")
		myPackageFolder.mkdir()
	}

	@After void teardown() {
		FileUtil.delete(rootFolder)
	}

	private static givenJarred(File dir, Closure closure) {
		def tempDir = FileUtil.createTempDirectory("", "")
		def jarFile = new File(tempDir, "plugin.jar")
		zip(dir, jarFile)
		try {
			closure(jarFile)
		} finally {
			FileUtil.delete(tempDir)
		}
	}

	private static def zip(File fileToZip, File destinationFile) {
		def zipOutputStream = new ZipOutputStream(new FileOutputStream(destinationFile))
		def allFilesFilter = new FileFilter() {
			@Override boolean accept(File pathName) { true }
		}
		ZipUtil.addDirToZipRecursively(zipOutputStream, null, fileToZip, "", allFilesFilter, new HashSet())
		zipOutputStream.close()
	}

	static createFile(String fileName, String fileContent, File directory) {
		def file = new File(directory, fileName)
		file.write(fileContent)
		file
	}

	static collectErrorsFrom(ErrorReporter errorReporter) {
		def errors = []
		errorReporter.reportAllErrors(new ErrorReporter.Callback() {
			@Override void display(String title, String message) {
				errors << [title, message]
			}
		})
		errors
	}
}
