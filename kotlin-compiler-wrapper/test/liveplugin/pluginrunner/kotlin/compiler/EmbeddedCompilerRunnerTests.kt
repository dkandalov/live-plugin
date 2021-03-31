package liveplugin.pluginrunner.kotlin.compiler

import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.script.experimental.annotations.KotlinScript

@Ignore // Run manually from current ContentRoot because KtsScriptFixture needs paths to stdlib and kotlin scripting jars
class EmbeddedCompilerRunnerTests {
    @Test fun `can compile an empty file`() = KtsScriptFixture(
        scriptSourceCode = ""
    ).run {
        assertThat(compileScript(), equalTo(emptyList()))
        assertTrue(outputFile.exists())
    }

    @Test fun `can compile println`() = KtsScriptFixture(
        scriptSourceCode = "println(123)"
    ).run {
        assertThat(compileScript(), equalTo(emptyList()))
        assertTrue(outputFile.exists())
    }

    @Test fun `can compile println of script template variable`() = KtsScriptFixture(
        scriptSourceCode = "println(foo.toString())",
        scriptTemplateClass = FooScriptTemplate::class.java
    ).run {
        assertThat(compileScript(), equalTo(emptyList()))
        assertTrue(outputFile.exists())
    }

    @Test fun `fails to compile unresolved reference`() = KtsScriptFixture(
        scriptSourceCode = "nonExistingFunction()"
    ).run {
        val errors = compileScript()
        assertThat(errors.size, equalTo(1))
        assertTrue(errors.first().contains("unresolved reference: nonExistingFunction"))
        assertFalse(outputFile.exists())
    }

    @Ignore
    @Test fun `KtVisitor shouldn't end up being incompatible with PsiElementVisitor`() = KtsScriptFixture(
        scriptSourceCode = """
            import org.jetbrains.kotlin.psi.*
            import com.intellij.psi.PsiElementVisitor

            println(KtVisitor::class)
            println(PsiElementVisitor::class)
            val foo: PsiElementVisitor = (expressionVisitor {} as KtVisitor<Void, Void>)
            println(foo)
        """
    ).run {
        assertThat(compileScript(), equalTo(emptyList()))
        assertTrue(outputFile.exists())
    }
}

private data class KtsScriptFixture(
    val scriptSourceCode: String,
    val scriptTemplateClass: Class<*> = EmptyScriptTemplate::class.java,
) {
    private val srcDir: File = Files.createTempDirectory("").toFile()
    private val outputDir: File = Files.createTempDirectory("").toFile()
    private val srcFile: File = File("${srcDir.absolutePath}/script.kts").also { it.writeText(scriptSourceCode) }
    private val kotlinStdLibPath: String = properties["kotlin-stdlib-path"]!!
    private val kotlinScriptRuntimePath: String = properties["kotlin-script-runtime-path"]!!
    private val kotlinScriptCommonPath: String = properties["kotlin-script-common-path"]!!
    private val kotlinScriptJvmPath: String = properties["kotlin-script-jvm"]!!
    private val kotlinScriptCompilerEmbeddablePath: String = properties["kotlin-script-compiler-embeddable-path"]!!
    private val kotlinScriptCompilerImplEmbeddablePath: String = properties["kotlin-script-compiler-impl-embeddable-path"]!!
    val outputFile get() = outputDir.resolve(srcFile.nameWithoutExtension.capitalize() + ".class")

    fun compileScript(): List<String> = compile(
        sourceRoot = srcDir.absolutePath,
        classpath = listOf(
                File(kotlinStdLibPath),
                File(kotlinScriptJvmPath),
                File(kotlinScriptCommonPath),
                File(kotlinScriptRuntimePath),
                File(kotlinScriptCompilerEmbeddablePath),
                File(kotlinScriptCompilerImplEmbeddablePath),
                File("../build/classes/kotlin/test/")
        ),
        outputDirectory = outputDir,
        livePluginScriptClass = scriptTemplateClass
    )

    companion object {
        private val properties by lazy {
            File("liveplugin/pluginrunner/kotlin/compiler/fixture.properties").readLines()
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
