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
import liveplugin.pluginrunner.GroovyPluginRunner

class AddToClassPathGroovyIntention: AddAfterImportsGroovyIntention(
    stringToInsert = GroovyPluginRunner.groovyAddToClasspathKeyword + "\n",
    modificationName = "Inserted 'add-to-classpath'",
    popupText = "Insert 'add-to-classpath' directive"
)

class AddPluginDependencyGroovyIntention: AddAfterImportsGroovyIntention(
    stringToInsert = GroovyPluginRunner.groovyDependsOnPluginKeyword + "\n",
    modificationName = "Inserted 'depends-on-plugin'",
    popupText = "Insert 'depends-on-plugin' directive"
)

open class AddAfterImportsGroovyIntention(
    private val stringToInsert: String,
    private val modificationName: String,
    private val popupText: String
): IntentionAction, DumbAware {

    private fun linesAboveCurrentAreImportOrPackage(editor: Editor): Boolean {
        val document = editor.document
        val offset = editor.caretModel.offset

        val lineNumberBeforeCurrent = document.getLineNumber(offset)

        return (0 until lineNumberBeforeCurrent)
            .asSequence()
            .map { lineNumber -> lineTextIn(document, lineNumber).trim { it <= ' ' } }
            .none { !it.isEmpty() && !it.startsWith("//") && !it.startsWith("/*") && !it.startsWith("import") && !it.startsWith("package") }
    }

    private fun isGroovyPluginScript(file: PsiFile): Boolean {
        val virtualFile = file.virtualFile
        return virtualFile != null && (virtualFile.name == GroovyPluginRunner.mainScript || virtualFile.name == GroovyPluginRunner.testScript)
    }

    private fun lineTextIn(document: Document, lineNumber: Int): String {
        val lineStartOffset = document.getLineStartOffset(lineNumber)
        val lineEndOffset = document.getLineEndOffset(lineNumber)
        return document.getText(TextRange(lineStartOffset, lineEndOffset))
    }

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean {
        return isGroovyPluginScript(file) && linesAboveCurrentAreImportOrPackage(editor)
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val document = editor.document
        val caretModel = editor.caretModel

        CommandProcessor.getInstance().executeCommand(project, {
            val lineNumber = document.getLineNumber(caretModel.offset)
            val lineStartOffset = document.getLineStartOffset(lineNumber)
            document.insertString(lineStartOffset, stringToInsert)
            caretModel.moveToOffset(lineStartOffset + stringToInsert.length - 1)
        }, modificationName, "LivePlugin", UndoConfirmationPolicy.DEFAULT, document)
    }

    override fun startInWriteAction() = true

    override fun getText() = popupText

    override fun getFamilyName() = modificationName
}
