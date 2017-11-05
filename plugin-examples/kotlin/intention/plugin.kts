
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import liveplugin.PluginUtil
import liveplugin.invokeLaterOnEDT
import liveplugin.show
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf

val javaIsSupportedByIde = Language.findLanguageByID("JAVA") != null
if (javaIsSupportedByIde) {
    PluginUtil.registerIntention(pluginDisposable, JavaFinalFieldIntention())
    if (!isIdeStartup) show("Reloaded 'Finalize Java Fields' intention")
}

class KotlinFunctionNameWithSpacesIntention: PsiElementBaseIntentionAction() {
    init {
        text = "KotlinFunctionNameWithSpacesIntention"
    }

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        return element.isInKotlinFile() &&
            element.parent != null &&
            KtNamedFunction::class.java.isAssignableFrom(element.parent.javaClass) &&
            element is LeafPsiElement &&
            element.elementType == KtTokens.IDENTIFIER
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        invokeLaterOnEDT {
            val function = element.parent
            val processor = RenamePsiElementProcessor.forElement(element)
            val dialog = processor.createRenameDialog(project, function, function, editor)
            dialog.setPreviewResults(false)
            try {
                val name: String = (function as KtNamedFunction).name!!
                val newName = if (name.contains(' ')) renameToCamelCase(name) else renameToSpaces(name)
                dialog.performRename(newName)
            } finally {
                dialog.close(DialogWrapper.CANCEL_EXIT_CODE) // to avoid dialog leak
            }
        }
    }

    private fun renameToCamelCase(name: String): String {
        val i = name.indexOf(' ')
        if (i == -1) return name
        if (i == name.length - 1) return name.dropLast(1)
        else return name.substring(0, i) + name[i].toUpperCase()
    }

    private fun renameToSpaces(name: String): String {
        val chars = name.toCharArray()
        if (chars.none { it == ' ' }) return name
//        return "`" + String(chars.flatMap {
//            if (Character.isUpperCase(it)) listOf(' ', it.toLowerCase()) else listOf(it)
//        }.toCharArray()) + "`"
        return "abc"
    }

    override fun getFamilyName() = "KotlinFunctionNameWithSpacesIntention"

    private fun PsiElement.isInKotlinFile(): Boolean {
        val fileType = (containingFile?.fileType as? LanguageFileType) ?: return false
        return fileType.language.id.toLowerCase() == "kotlin"
    }

    private fun <T> PsiElement?.findParent(aClass: Class<T>): T? = when {
        this == null -> null
        aClass.isAssignableFrom(this.javaClass) -> this as T
        else -> this.parent.findParent(aClass)
    }
}

/**
 * See also in IJ sources [com.siyeh.ig.fixes.MakeFieldFinalFix].
 */
class JavaFinalFieldIntention: PsiElementBaseIntentionAction() {
    private var isFinal = false

    override fun isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean {
        if (!element.isInJavaFile()) return false
        val field = element.findParent(PsiField::class) ?: return false

        isFinal = field.hasModifierProperty("final")
        text = if (isFinal) "Make 'non-final'" else "Make 'final'"
        return true
    }

    override fun invoke(project: Project, editor: Editor, element: PsiElement) {
        val modifiers = element.findParent(PsiField::class)?.modifierList ?: return
        modifiers.setModifierProperty("final", !isFinal)
    }

    override fun getFamilyName() = "Make Java Field (Non-)Final"

    private fun <T: PsiElement> PsiElement?.findParent(aClass: KClass<T>): T? = when {
        this == null -> null
        aClass.isSuperclassOf(this::class) -> this as T
        else -> parent?.findParent(aClass)
    }

    private fun PsiElement.isInJavaFile(): Boolean {
        val fileType = containingFile?.fileType ?: return false
        return fileType is LanguageFileType && fileType.language.id == "JAVA"
    }
}