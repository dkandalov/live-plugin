package liveplugin

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import liveplugin.LivePluginAppComponent.Companion.livePluginId
import liveplugin.pluginrunner.GroovyPluginRunner
import liveplugin.pluginrunner.GroovyPluginRunner.Companion.groovyAddToClasspathKeyword
import liveplugin.pluginrunner.GroovyPluginRunner.Companion.groovyDependsOnPluginKeyword
import liveplugin.pluginrunner.kotlin.KotlinPluginRunner
import liveplugin.pluginrunner.kotlin.KotlinPluginRunner.Companion.kotlinAddToClasspathKeyword
import liveplugin.pluginrunner.kotlin.KotlinPluginRunner.Companion.kotlinDependsOnPluginKeyword

class AddToClassPathGroovyIntention: AddAfterImportsGroovyIntention(
    stringToInsert = groovyAddToClasspathKeyword + "\n",
    modificationName = "Inserted 'add-to-classpath'",
    popupText = "Insert 'add-to-classpath' directive",
    isAvailable = isAvailableForGroovy
)

class AddPluginDependencyGroovyIntention: AddAfterImportsGroovyIntention(
    stringToInsert = groovyDependsOnPluginKeyword + "\n",
    modificationName = "Inserted 'depends-on-plugin'",
    popupText = "Insert 'depends-on-plugin' directive",
    isAvailable = isAvailableForGroovy
)

class AddToClassPathKotlinIntention: AddAfterImportsGroovyIntention(
    stringToInsert = kotlinAddToClasspathKeyword + "\n",
    modificationName = "Inserted 'add-to-classpath'",
    popupText = "Insert 'add-to-classpath' directive",
    isAvailable = isAvailableForKotlin
)

class AddPluginDependencyKotlinIntention: AddAfterImportsGroovyIntention(
    stringToInsert = kotlinDependsOnPluginKeyword + "\n",
    modificationName = "Inserted 'depends-on-plugin'",
    popupText = "Insert 'depends-on-plugin' directive",
    isAvailable = isAvailableForKotlin
)

val isAvailableForGroovy = { editor: Editor, file: PsiFile ->
    isGroovyPluginScript(file) && linesAboveCurrentAreImportOrPackage(editor)
}

val isAvailableForKotlin = { editor: Editor, file: PsiFile ->
    isKotlinPluginScript(file) && linesAboveCurrentAreImportOrPackage(editor)
}

open class AddAfterImportsGroovyIntention(
    private val stringToInsert: String,
    private val modificationName: String,
    private val popupText: String,
    private val isAvailable: (editor: Editor, file: PsiFile) -> Boolean
): IntentionAction, DumbAware {

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile) = isAvailable(editor, file)

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val document = editor.document
        val caretModel = editor.caretModel

        CommandProcessor.getInstance().executeCommand(project, {
            val lineNumber = document.getLineNumber(caretModel.offset)
            val lineStartOffset = document.getLineStartOffset(lineNumber)
            document.insertString(lineStartOffset, stringToInsert)
            caretModel.moveToOffset(lineStartOffset + stringToInsert.length - 1)
        }, modificationName, livePluginId, UndoConfirmationPolicy.DEFAULT, document)
    }

    override fun startInWriteAction() = true

    override fun getText() = popupText

    override fun getFamilyName() = modificationName
}


private fun isGroovyPluginScript(file: PsiFile): Boolean {
    val virtualFile = file.virtualFile ?: return false
    return virtualFile.name == GroovyPluginRunner.mainScript || virtualFile.name == GroovyPluginRunner.testScript
}

private fun isKotlinPluginScript(file: PsiFile): Boolean {
    val virtualFile = file.virtualFile ?: return false
    return virtualFile.name == KotlinPluginRunner.mainScript || virtualFile.name == KotlinPluginRunner.testScript
}

private fun linesAboveCurrentAreImportOrPackage(editor: Editor): Boolean {
    val document = editor.document
    val offset = editor.caretModel.offset

    val lineNumberBeforeCurrent = document.getLineNumber(offset)

    return (0 until lineNumberBeforeCurrent)
        .asSequence()
        .map { lineNumber -> document.lineTextAt(lineNumber).trim { it <= ' ' } }
        .none { !it.isEmpty() && !it.startsWith("//") && !it.startsWith("/*") && !it.startsWith("import") && !it.startsWith("package") }
}

private fun Document.lineTextAt(lineNumber: Int): String {
    val lineStartOffset = getLineStartOffset(lineNumber)
    val lineEndOffset = getLineEndOffset(lineNumber)
    return getText(TextRange(lineStartOffset, lineEndOffset))
}
