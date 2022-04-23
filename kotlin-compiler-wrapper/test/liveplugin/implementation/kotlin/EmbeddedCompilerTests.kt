package liveplugin.implementation.kotlin

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.script.experimental.annotations.KotlinScript

@Ignore // Run manually from current ContentRoot because KtsScriptFixture needs paths to stdlib and kotlin scripting jars
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
    private val kotlinStdLibPath: String = properties["kotlin-stdlib-path"]!!
    private val kotlinScriptRuntimePath: String = properties["kotlin-script-runtime-path"]!!
    private val kotlinScriptCommonPath: String = properties["kotlin-script-common-path"]!!
    private val kotlinScriptJvmPath: String = properties["kotlin-script-jvm"]!!
    private val kotlinScriptCompilerEmbeddablePath: String = properties["kotlin-script-compiler-embeddable-path"]!!
    private val kotlinScriptCompilerImplEmbeddablePath: String = properties["kotlin-script-compiler-impl-embeddable-path"]!!
    val outputDir: File = Files.createTempDirectory("").toFile()

    fun createFile(name: String, text: String) {
        File("${srcDir.absolutePath}/${name}").writeText(text)
    }
    
    fun compileScript(templateClass: Class<*> = EmptyScriptTemplate::class.java): List<String> = compile(
        sourceRoot = srcDir.absolutePath,
        classpath = listOf(
            File(kotlinStdLibPath),
            File(kotlinScriptJvmPath),
            File(kotlinScriptCommonPath),
            File(kotlinScriptRuntimePath),
            File(kotlinScriptCompilerEmbeddablePath),
            File(kotlinScriptCompilerImplEmbeddablePath),
            File(srcDir.absolutePath), // Because this is what KotlinPluginCompiler class is doing.
            File("build/classes/kotlin/test/") // For EmptyScriptTemplate and FooScriptTemplate classes
        ),
        outputDirectory = outputDir,
        livePluginScriptClass = templateClass
    )

    companion object {
        private val properties by lazy {
            File("test/liveplugin/implementation/kotlin/fixture.properties").readLines()
                .map { line -> line.split("=") }
                .map { (key, value) -> key to value.replace("\$USER_HOME", System.getProperty("user.home")) }
                .onEach { (_, value) -> if (!File(value).exists()) error("File doesn't exist: $value") }
                .toMap()
        }
    }
}

@KotlinScript
class EmptyScriptTemplate

@Suppress("unused")
@KotlinScript
abstract class FooScriptTemplate(val foo: Int)
