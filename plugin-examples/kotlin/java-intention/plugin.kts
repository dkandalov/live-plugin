
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import liveplugin.PluginUtil
import liveplugin.show
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf


// See also com.siyeh.ig.fixes.MakeFieldFinalFix in IntelliJ sources.

val javaIsSupportedByIde = Language.findLanguageByID("JAVA") != null
if (javaIsSupportedByIde) {
    PluginUtil.registerIntention(pluginDisposable, MakeJavaFieldFinalIntention())
    PluginUtil.registerIntention(pluginDisposable, MakeJavaFieldNonFinalIntention())
    if (!isIdeStartup) show("Reloaded MakeJavaFieldFinalIntention")
} else {
    if (!isIdeStartup) show("IDE doesn't support Java")
}

class MakeJavaFieldFinalIntention: PsiElementBaseIntentionAction() {
    override fun isAvailable(project: Project, editor: Editor, element: PsiElement) =
        element.isInJavaFile() && element.findParent(PsiField::class)?.hasModifierProperty("final") == false

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        element.findParent(PsiField::class)?.modifierList?.setModifierProperty("final", true)
    }

    override fun getText() = "Make 'final'"
    override fun getFamilyName() = "Make Java Field (Non-)Final"
}

class MakeJavaFieldNonFinalIntention: PsiElementBaseIntentionAction() {
    override fun isAvailable(project: Project, editor: Editor, element: PsiElement) =
        element.isInJavaFile() && element.findParent(PsiField::class)?.hasModifierProperty("final") == true

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        element.findParent(PsiField::class)?.modifierList?.setModifierProperty("final", false)
    }

    override fun getText() = "Make 'non-final'"
    override fun getFamilyName() = "Make Java Field (Non-)Final"
}

fun PsiElement.isInJavaFile(): Boolean {
    val fileType = containingFile?.fileType ?: return false
    return fileType is LanguageFileType && fileType.language.id == "JAVA"
}

@Suppress("UNCHECKED_CAST")
fun <T: PsiElement> PsiElement?.findParent(aClass: KClass<T>): T? = when {
    this == null -> null
    aClass.isSuperclassOf(this::class) -> this as T
    else -> parent.findParent(aClass)
}
