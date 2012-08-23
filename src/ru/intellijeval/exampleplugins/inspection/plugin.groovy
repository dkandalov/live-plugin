import com.intellij.codeInsight.daemon.GroupNames
import com.intellij.codeInspection.BaseJavaLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.ex.InspectionToolRegistrar
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.ide.ui.search.SearchableOptionsRegistrar
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.notification.NotificationsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Factory
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiLiteralExpression


// TODO review this

class SwearDetector extends BaseJavaLocalInspectionTool {
	@Override String getGroupDisplayName() { GroupNames.BUGS_GROUP_NAME }

	@Override String getDisplayName() { "Detects swear words" }

	@Override String getShortName() { "SwearDetector" }

	@Override boolean isEnabledByDefault() { true }

	@Override PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {
		new JavaElementVisitor() {
			@Override void visitLiteralExpression(PsiLiteralExpression expression) {
				super.visitLiteralExpression(expression)
				if (expression.type.equalsToText("java.lang.String") && expression.value == "asdf") {
					holder.registerProblem(expression, "Found swear word")
				}
			}
		}
	}
}

static show(String htmlBody, String title = "", notificationType = NotificationType.INFORMATION) {
	def notification = new Notification("", title, htmlBody, notificationType)
	((Notifications) NotificationsManager.notificationsManager).notify(notification)
}


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

addInspection(event.project, { new SwearDetector() })

show("eval inspection")