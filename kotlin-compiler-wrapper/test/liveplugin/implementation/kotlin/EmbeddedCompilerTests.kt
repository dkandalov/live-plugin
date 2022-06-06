package liveplugin.implementation.kotlin

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.script.experimental.annotations.KotlinScript

class EmbeddedCompilerTests {
    @Test fun `compile an empty file`() = KtsScriptFixture().run {
        createFile("plugin.kts", text = "")
        assertThat(compileScript(), equalTo(emptyList()))
        assertTrue(outputDir.resolve("Plugin.class").exists())
    }

    @Test fun `compile println`() = KtsScriptFixture().run {
        createFile("plugin.kts", text = "println(123)")
        assertThat(compileScript(), equalTo(emptyList()))
        assertTrue(outputDir.resolve("Plugin.class").exists())
    }

    @Test fun `compile kts and a text file`() = KtsScriptFixture().run {
        createFile("plugin.kts", text = "println(123)")
        createFile("some.txt", text = "foo\nbar")

        assertThat(compileScript(), equalTo(emptyList()))
        assertTrue(outputDir.resolve("Plugin.class").exists())
        assertFalse(outputDir.resolve("some.txt").exists())
    }

    @Test fun `compile two kts files`() = KtsScriptFixture().run {
        createFile("plugin.kts", text = "println(123)")
        createFile("some.kts", text = "println(456)")

        assertThat(compileScript(), equalTo(emptyList()))
        assertTrue(outputDir.resolve("Plugin.class").exists())
        assertTrue(outputDir.resolve("Some.class").exists())
    }

    @Test fun `compile println with script template variable`() = KtsScriptFixture().run {
        createFile("plugin.kts", text = "println(foo.toString())")
        assertThat(compileScript(templateClass = FooScriptTemplate::class.java), equalTo(emptyList()))
        assertTrue(outputDir.resolve("Plugin.class").exists())
    }

    @Test fun `fail to compile unresolved reference`() = KtsScriptFixture().run {
        createFile("plugin.kts", text = "nonExistingFunction()")

        val errors = compileScript()
        assertThat(errors.size, equalTo(1))
        assertTrue(errors.first().contains("unresolved reference: nonExistingFunction"))
        assertFalse(outputDir.resolve("Plugin.class").exists())
    }

    @Ignore
    @Test fun `KtVisitor shouldn't end up being incompatible with PsiElementVisitor`() = KtsScriptFixture().run {
        createFile("plugin.kts", text = """
            import org.jetbrains.kotlin.psi.*
            import com.intellij.psi.PsiElementVisitor

            println(KtVisitor::class)
            println(PsiElementVisitor::class)
            val foo: PsiElementVisitor = (expressionVisitor {} as KtVisitor<Void, Void>)
            println(foo)
        """)

        assertThat(compileScript(), equalTo(emptyList()))
        assertTrue(outputDir.resolve("Plugin.class").exists())
    }
}

private class KtsScriptFixture {
    private val srcDir: File = Files.createTempDirectory("").toFile()
    val outputDir: File = Files.createTempDirectory("").toFile()

    fun createFile(name: String, text: String) {
        File("${srcDir.absolutePath}/${name}").writeText(text)
    }

    fun compileScript(templateClass: Class<*> = EmptyScriptTemplate::class.java): List<String> = compile(
        sourceRoot = srcDir.absolutePath,
        classpath = listOf(
            kotlinStdLib,
            kotlinScriptJvm,
            kotlinScriptingCommon,
            kotlinScriptRuntime,
            kotlinScriptCompilerEmbeddable,
            kotlinScriptCompilerImplEmbeddable,
            File("out/test/classes"), // For running via IntelliJ
            File("build/classes/kotlin/test"), // For running via gradle
        ),
        outputDirectory = outputDir,
        livePluginScriptClass = templateClass
    )

    companion object {
        private val kotlinStdLib = findInGradleCache("kotlin-stdlib")
        private val kotlinScriptRuntime = findInGradleCache("kotlin-script-runtime")
        private val kotlinScriptingCommon = findInGradleCache("kotlin-scripting-common")
        private val kotlinScriptJvm = findInGradleCache("kotlin-scripting-jvm")
        private val kotlinScriptCompilerEmbeddable = findInGradleCache("kotlin-scripting-compiler-embeddable")
        private val kotlinScriptCompilerImplEmbeddable = findInGradleCache("kotlin-scripting-compiler-impl-embeddable")

        private fun findInGradleCache(libName: String): File {
            val kotlinVersion = "1.6.21"
            val dir = File(System.getProperty("user.home") + "/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin")
            val libDir = dir.listFiles()?.find { it.name == libName } ?: error("Couldn't find $libName in $dir")
            val libVersionDir = libDir.listFiles()?.find { it.name == kotlinVersion } ?: error("Couldn't find $kotlinVersion in $libDir")
            val jarFileName = "$libName-$kotlinVersion.jar"
            return libVersionDir.walkTopDown().find { it.name == jarFileName } ?: error("Couldn't find $jarFileName in $libVersionDir")
        }
    }
}

@KotlinScript
class EmptyScriptTemplate

@KotlinScript
abstract class FooScriptTemplate(val foo: Int)
