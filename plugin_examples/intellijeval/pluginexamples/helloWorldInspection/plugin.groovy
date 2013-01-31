import com.intellij.codeInsight.daemon.GroupNames
import com.intellij.codeInspection.BaseJavaLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolRegistrar
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.ide.ui.search.SearchableOptionsRegistrar
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Factory
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiLiteralExpression

import static intellijeval.PluginUtil.show


static addInspection(Project project, Closure<LocalInspectionTool> inspectionFactory) {
	def registrar = new InspectionToolRegistrar(SearchableOptionsRegistrar.instance)
	registrar.registerInspectionToolFactory(new Factory<InspectionToolWrapper>() {
		@Override InspectionToolWrapper create() {
			new LocalInspectionToolWrapper(inspectionFactory.call())
		}
	}, true)

	// there is also InspectionProfileManager which keeps global IDE profiles
	def projectProfileManager = InspectionProjectProfileManager.getInstance(project)
	def oldProfile = projectProfileManager.inspectionProfile

	// create new profile to make it ask registrar for newly added inspection
	def newProfile = new InspectionProfileImpl(oldProfile.name, registrar, projectProfileManager)
	newProfile.copyFrom(oldProfile)
	newProfile.baseProfile = null // can break if baseProfile is set to default profile? (e.g. in com.intellij.codeInspection.ex.InspectionProfileImpl#readExternal)
	newProfile.initInspectionTools(null)

	projectProfileManager.deleteProfile(oldProfile.name)
	projectProfileManager.updateProfile(newProfile)
	projectProfileManager.projectProfile = newProfile.name
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


addInspection(event.project, { new HelloWorldInspection() })

show("Loaded hello world inspection<br/>It replaces \"hello\" string literal in java code with \"Hello world\"")