
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.refactoring.rename.RenamePsiElementProcessor
import liveplugin.registerIntention
import liveplugin.runLaterOnEdt
import liveplugin.show
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction

// depends-on-plugin org.jetbrains.kotlin

registerIntention(RenameKotlinFunctionToUseSpacesIntention())
registerIntention(RenameKotlinFunctionToUseCamelCaseIntention())
if (!isIdeStartup) show("Reloaded Kotlin intentions")

inner class RenameKotlinFunctionToUseSpacesIntention: PsiElementBaseIntentionAction() {
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement) =
        element.isInKotlinFile() && element.findKtNamedFunction()?.name?.contains(' ') == false

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        doRenameRefactoring(element, project, editor, ::camelCaseToSpaces)
    }

    private fun camelCaseToSpaces(name: String): String {
        val newName = name.flatMap { char ->
            if (char.isLowerCase()) listOf(char)
            else listOf(' ', char.lowercaseChar())
        }.joinToString("")
        return "`$newName`"
    }

    override fun getText() = "Rename to use spaces"
    override fun getFamilyName() = "RenameKotlinFunctionIntention"
}

inner class RenameKotlinFunctionToUseCamelCaseIntention: PsiElementBaseIntentionAction() {
    override fun isAvailable(project: Project, editor: Editor?, element: PsiElement) =
        element.isInKotlinFile() && element.findKtNamedFunction()?.name?.contains(' ') == true

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        doRenameRefactoring(element, project, editor, this::spacesToCamelCase)
    }

    private fun spacesToCamelCase(name: String): String {
        return name.split(' ')
            .filter { it.isNotEmpty() }
            .mapIndexed { i, word ->
                if (i == 0) word else word.replaceFirstChar { it.uppercaseChar() }
            }
            .joinToString("")
    }

    override fun getText() = "Rename to use camel case"
    override fun getFamilyName() = "RenameKotlinFunctionIntention"
}

fun doRenameRefactoring(element: PsiElement, project: Project, editor: Editor?, rename: (String) -> String) {
    val function = element.findKtNamedFunction()!!
    val newName = rename(function.name!!)

    val processor = RenamePsiElementProcessor.forElement(element)
    val renameDialog = processor.createRenameDialog(project, function as PsiElement, function as PsiElement, editor)
    renameDialog.setPreviewResults(false)
    runLaterOnEdt {
        try {
            renameDialog.performRename(newName)
        } finally {
            renameDialog.close(DialogWrapper.CANCEL_EXIT_CODE) // to avoid dialog leak
        }
    }
}

fun PsiElement.findKtNamedFunction(): KtNamedFunction? {
    val isOnFunctionIdentifier =
        this is LeafPsiElement &&
            elementType == KtTokens.IDENTIFIER &&
            parent.let {
                it != null && KtNamedFunction::class.java.isAssignableFrom(it.javaClass)
            }
    return if (isOnFunctionIdentifier) parent as KtNamedFunction else null
}

fun PsiElement.isInKotlinFile(): Boolean {
    val fileType = (containingFile?.fileType as? LanguageFileType) ?: return false
    return fileType.language.id.lowercase() == "kotlin"
}
