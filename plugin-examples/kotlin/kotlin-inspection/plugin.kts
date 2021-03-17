import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import liveplugin.registerInspection
import liveplugin.show
import org.jetbrains.kotlin.idea.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.psi.*

// depends-on-plugin org.jetbrains.kotlin

registerInspection(HelloWorldInspectionKotlin())
if (!isIdeStartup) {
    show("Loaded hello world inspection<br/>It replaces \"hello\" string literal in Java code with \"Hello world\"")
}

class HelloWorldInspectionKotlin : AbstractKotlinInspection() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return expressionVisitor { expression: KtExpression ->
            if (expression is KtStringTemplateExpression && expression.text == "\"hello\"") {
                holder.registerProblem(expression, "Found \"hello\"", HelloWorldQuickFix())
            }
        }
    }
    override fun getDisplayName() = "Replace \"hello\" with \"Hello world\" in Kotlin"
    override fun getShortName() = "HelloWorldInspectionKotlin"
    override fun getGroupDisplayName() = "Live Plugin"
    override fun isEnabledByDefault() = true
}

class HelloWorldQuickFix : LocalQuickFix {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val stringLiteral = KtPsiFactory(descriptor.psiElement).createExpressionByPattern("\"Hello World\"")
        descriptor.psiElement.replace(stringLiteral)
    }
    override fun getName() = "Replace with \"Hello World\""
    override fun getFamilyName() = name
}
