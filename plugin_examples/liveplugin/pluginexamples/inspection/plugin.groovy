import com.intellij.codeInsight.daemon.GroupNames
import com.intellij.codeInspection.BaseJavaLocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiLiteralExpression

import static liveplugin.PluginUtil.registerInspection
import static liveplugin.PluginUtil.show

// Please note this example will only work in IDEs without Java support.

registerInspection(pluginDisposable, new HelloWorldInspection())

if (!isIdeStartup) {
	show("Loaded hello world inspection<br/>It replaces \"hello\" string literal in Java code with \"Hello world\"")
}


class HelloWorldInspection extends BaseJavaLocalInspectionTool {
	@Override String getGroupDisplayName() { GroupNames.BUGS_GROUP_NAME }
	@Override String getDisplayName() { 'Replace "hello" with "Hello world"' }
	@Override String getShortName() { "HelloWorldInspection" }
	@Override boolean isEnabledByDefault() { true }

	@Override PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {
		new JavaElementVisitor() {
			@Override void visitLiteralExpression(PsiLiteralExpression expression) {
				super.visitLiteralExpression(expression)
				if (expression.type.equalsToText("java.lang.String") && expression.value == "hello") {
					holder.registerProblem(expression, "Found hello word", new HelloWorldQuickFix())
				}
			}
		}
	}
}

class HelloWorldQuickFix implements LocalQuickFix {
	@Override String getName() { 'Replace with "Hello World"' }
	@Override String getFamilyName() { name }

	@Override void applyFix(Project project, ProblemDescriptor descriptor) {
		def factory = JavaPsiFacade.getInstance(project).elementFactory
		def stringLiteral = factory.createExpressionFromText('"Hello World"', null)
		descriptor.psiElement.replace(stringLiteral)
	}
}

