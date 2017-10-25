
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import liveplugin.PluginUtil
import liveplugin.show

PluginUtil.registerIntention("MakeFieldFinal", JavaFinalFieldIntentionAction("Make 'final'", true))
PluginUtil.registerIntention("MakeFieldNonFinal", JavaFinalFieldIntentionAction("Make 'non-final'", false))

if (!isIdeStartup) show("Reloaded 'Finalize Java Fields' plugin")


class JavaFinalFieldIntentionAction(private val text: String, private val addFinal: Boolean): PsiElementBaseIntentionAction() {

    override fun invoke(project: Project, editor: Editor, psiElement: PsiElement) {
        val modifiers = psiElement.findParent(PsiField::class.java)?.modifierList ?: return
        if (modifiers.hasModifierProperty("final") != addFinal) {
            modifiers.setModifierProperty("final", addFinal)
        }
    }

    override fun isAvailable(project: Project, editor: Editor, psiElement: PsiElement): Boolean {
        val field = psiElement.findParent(PsiField::class.java)
        return field?.hasModifierProperty("final") != addFinal
    }

    override fun getFamilyName(): String {
        val prefix = if (addFinal) "" else "Non"
        return "MakeField${prefix}Final"
    }

    override fun getText() = text

    private fun <T: PsiElement> PsiElement?.findParent(aClass: Class<T>): T? =
        if (this == null) null
        else if (aClass.isAssignableFrom(this.javaClass)) this as T
        else this.parent.findParent(aClass)
}