import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiJavaFile
import liveplugin.testrunner.IntegrationTestsRunner
import org.junit.Ignore
import org.junit.Test

// This is example of integration test.
// The idea is that for code which heavily uses IntelliJ API it can be faster
// to run tests inside IntelliJ without need for potentially long or complex initialisation.

// "plugin-test.groovy" is an entry point just like "plugin.groovy",
// therefore running tests has to be done manually, e.g. using IntegrationTestsRunner.runIntegrationTests().
// (Note that this is not original JUnit runner and it only supports @Test and @Ignore annotations.)
IntegrationTestsRunner.runIntegrationTests([ExampleTest, ExamplePsiTest], project, pluginPath)


class ExampleTest {
	@Test void "passing test"() {
		assert 123 == 123
	}

	@Test void "failing test"() {
		assert 123 == 234
	}

	@Test void "error test"() {
		throw new Exception("dummy exception")
	}

	@Ignore @Test void "ignored test"() {
		throw new Exception("dummy exception")
	}
}

// (Please note this test won't work in IDEs without Java support.
// You might be able to run it by removing all code referring to Java,
// although it's probably better to write plugins in IDE with Groovy and Java support.)
class ExamplePsiTest {
	@Test void "count amount of PsiElements in Java file"() {
		def javaPsi = parseAsJavaPsi("Sample.java", """
			class Sample {
				Sample() {}
				Sample(int i) {}
				void method(int i1, int i2) {}
			}
		""")
		assert countElementsIn(javaPsi) == 77
	}

	private static int countElementsIn(PsiJavaFile javaPsi) {
		int elementsCount = 0
		javaPsi.acceptChildren(new JavaRecursiveElementVisitor() {
			@Override void visitElement(PsiElement element) {
				super.visitElement(element)
				elementsCount++
			}
		})
		elementsCount
	}

	private PsiJavaFile parseAsJavaPsi(String fileName, String javaCode) {
		def fileFactory = PsiFileFactory.getInstance(project)
		fileFactory.createFileFromText(fileName, JavaFileType.INSTANCE, javaCode) as PsiJavaFile
	}

	// "context" is optional argument passed in by test runner
	ExamplePsiTest(Map context) {
		this.project = context.project
		this.pluginPath = context.pluginPath
	}

	private final Project project
	private final String pluginPath
}