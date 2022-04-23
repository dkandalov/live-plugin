package liveplugin.implementation

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import liveplugin.implementation.LivePluginAppComponent.Companion.livePluginId
import liveplugin.implementation.pluginrunner.groovy.GroovyPluginRunner
import liveplugin.implementation.pluginrunner.groovy.GroovyPluginRunner.Companion.groovyAddToClasspathKeyword
import liveplugin.implementation.pluginrunner.groovy.GroovyPluginRunner.Companion.groovyDependsOnPluginKeyword
import liveplugin.implementation.pluginrunner.kotlin.KotlinPluginRunner
import liveplugin.implementation.pluginrunner.kotlin.KotlinPluginRunner.Companion.kotlinAddToClasspathKeyword
import liveplugin.implementation.pluginrunner.kotlin.KotlinPluginRunner.Companion.kotlinDependsOnPluginKeyword

class AddToClassPathGroovyIntention: AddAfterImportsIntention(
    stringToInsert = groovyAddToClasspathKeyword + "\n",
    intentionName = "Insert 'add-to-classpath' directive for Groovy",
    popupText = "Insert 'add-to-classpath'",
    isAvailable = availableInGroovyLivePlugins
)

class AddPluginDependencyGroovyIntention: AddAfterImportsIntention(
    stringToInsert = groovyDependsOnPluginKeyword + "\n",
    intentionName = "Insert 'depends-on-plugin' directive for Groovy",
    popupText = "Insert 'depends-on-plugin'",
    isAvailable = availableInGroovyLivePlugins
)

class AddToClassPathKotlinIntention: AddAfterImportsIntention(
    stringToInsert = kotlinAddToClasspathKeyword + "\n",
    intentionName = "Insert 'add-to-classpath' directive for Kotlin",
    popupText = "Insert 'add-to-classpath'",
    isAvailable = availableInKotlinLivePlugins
)

class AddPluginDependencyKotlinIntention: AddAfterImportsIntention(
    stringToInsert = kotlinDependsOnPluginKeyword + "\n",
    intentionName = "Insert 'depends-on-plugin' directive for Kotlin",
    popupText = "Insert 'depends-on-plugin'",
    isAvailable = availableInKotlinLivePlugins
)

open class AddAfterImportsIntention(
    private val stringToInsert: String,
    private val intentionName: String,
    private val popupText: String,
    private val isAvailable: (editor: Editor, file: PsiFile) -> Boolean
): IntentionAction, DumbAware {

    override fun isAvailable(project: Project, editor: Editor, file: PsiFile) = isAvailable(editor, file)

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        val document = editor.document
        val caretModel = editor.caretModel

        val runnable = {
            val lineNumber = document.getLineNumber(caretModel.offset)
            val lineStartOffset = document.getLineStartOffset(lineNumber)
            document.insertString(lineStartOffset, stringToInsert)
            caretModel.moveToOffset(lineStartOffset + stringToInsert.length - 1)
        }
        CommandProcessor.getInstance()
            .executeCommand(project, runnable, popupText, livePluginId, UndoConfirmationPolicy.DEFAULT, document)
    }

    override fun startInWriteAction() = true

    override fun getText() = popupText

    override fun getFamilyName() = intentionName
}

private val availableInGroovyLivePlugins = { editor: Editor, file: PsiFile ->
    isGroovyPluginScript(file) && linesAboveCurrentAreImportOrPackage(editor)
}

private val availableInKotlinLivePlugins = { editor: Editor, file: PsiFile ->
    isKotlinPluginScript(file) && linesAboveCurrentAreImportOrPackage(editor)
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
        .none { it.isNotEmpty() && !it.startsWith("//") && !it.startsWith("/*") && !it.startsWith("import") && !it.startsWith("package") }
}

private fun Document.lineTextAt(lineNumber: Int): String {
    val lineStartOffset = getLineStartOffset(lineNumber)
    val lineEndOffset = getLineEndOffset(lineNumber)
    return getText(TextRange(lineStartOffset, lineEndOffset))
}
