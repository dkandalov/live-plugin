
import com.intellij.codeInspection.*
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiLiteralExpression
import liveplugin.registerInspection
import liveplugin.show

// depends-on-plugin com.intellij.java

registerInspection(HelloWorldInspection())
if (!isIdeStartup) {
    show("Loaded hello world inspection<br/>It replaces \"hello\" string literal in Java code with \"Hello world\"")
}

class HelloWorldInspection: AbstractBaseJavaLocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) = object: JavaElementVisitor() {
        override fun visitLiteralExpression(expression: PsiLiteralExpression) {
            super.visitLiteralExpression(expression)
            if (expression.type?.equalsToText("java.lang.String") == true && expression.value == "hello") {
                holder.registerProblem(expression, "Found hello word", HelloWorldQuickFix())
            }
        }
    }

    override fun getDisplayName() = "Replace \"hello\" with \"Hello world\""
    override fun getShortName() = "HelloWorldInspection"
    override fun getGroupDisplayName() = InspectionsBundle.message("group.names.probable.bugs")
    override fun isEnabledByDefault() = true
}

class HelloWorldQuickFix: LocalQuickFix {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val factory = JavaPsiFacade.getInstance(project).elementFactory
        val stringLiteral = factory.createExpressionFromText("\"Hello World\"", null)
        descriptor.psiElement.replace(stringLiteral)
    }

    override fun getName() = "Replace with \"Hello World\""
    override fun getFamilyName() = name
}
