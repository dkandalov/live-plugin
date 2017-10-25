
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import liveplugin.PluginUtil
import liveplugin.show

val javaIsSupportedByIde = Language.findLanguageByID("JAVA") != null
if (javaIsSupportedByIde) {
    PluginUtil.registerIntention("MakeFieldFinal", JavaFinalFieldIntentionAction(addFinal = true))
    PluginUtil.registerIntention("MakeFieldNonFinal", JavaFinalFieldIntentionAction(addFinal = false))
    if (!isIdeStartup) show("Reloaded 'Finalize Java Fields' plugin")
}

/**
 * See also in IJ sources [com.siyeh.ig.fixes.MakeFieldFinalFix].
 */
class JavaFinalFieldIntentionAction(private val addFinal: Boolean): PsiElementBaseIntentionAction() {

    override fun invoke(project: Project, editor: Editor, psiElement: PsiElement) {
        val modifiers = psiElement.findParent(PsiField::class.java)?.modifierList ?: return
        if (modifiers.hasModifierProperty("final") != addFinal) {
            modifiers.setModifierProperty("final", addFinal)
        }
    }

    override fun isAvailable(project: Project, editor: Editor, psiElement: PsiElement): Boolean {
        if (!psiElement.containingFile.fileType.let{ it is LanguageFileType && it.language.id == "JAVA"}) return false
        val field = psiElement.findParent(PsiField::class.java)
        return field?.hasModifierProperty("final") != addFinal
    }

    override fun getText(): String {
        val prefix = if (addFinal) "non-" else ""
        return "Make '${prefix}final'"
    }

    override fun getFamilyName() = "Make Java Field Final"

    private fun <T> PsiElement?.findParent(aClass: Class<T>): T? = when {
        this == null -> null
        aClass.isAssignableFrom(this.javaClass) -> this as T
        else -> this.parent.findParent(aClass)
    }
}