
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.util.parentOfType
import liveplugin.registerIntention
import liveplugin.show

// depends-on-plugin com.intellij.java

// See also com.siyeh.ig.fixes.MakeFieldFinalFix in IntelliJ sources.

registerIntention(MakeJavaFieldFinalIntention())
registerIntention(MakeJavaFieldNonFinalIntention())
if (!isIdeStartup) show("Reloaded MakeJavaFieldFinalIntention")

class MakeJavaFieldFinalIntention: PsiElementBaseIntentionAction() {
    override fun isAvailable(project: Project, editor: Editor, element: PsiElement) =
        element.isInJavaFile() && element.parentPsiField()?.hasModifierProperty("final") == false

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        element.parentPsiField()?.modifierList?.setModifierProperty("final", true)
    }

    override fun getText() = "Make 'final'"
    override fun getFamilyName() = "Make Java field (non-)final"
}

class MakeJavaFieldNonFinalIntention: PsiElementBaseIntentionAction() {
    override fun isAvailable(project: Project, editor: Editor, element: PsiElement) =
        element.isInJavaFile() && element.parentPsiField()?.hasModifierProperty("final") == true

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        element.parentPsiField()?.modifierList?.setModifierProperty("final", false)
    }

    override fun getText() = "Make 'non-final'"
    override fun getFamilyName() = "Make Java field (non-)final"
}

fun PsiElement.isInJavaFile(): Boolean {
    val fileType = containingFile?.fileType ?: return false
    return fileType is LanguageFileType && fileType.language.id == "JAVA"
}

fun PsiElement.parentPsiField() = parentOfType<PsiField>()
