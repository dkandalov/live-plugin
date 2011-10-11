package ru.intellijeval;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.editor.Editor;
import groovy.lang.GroovyShell;

/**
 * @author DKandalov
 */
public class EvaluateAction extends AnAction {
	@Override
	public void actionPerformed(AnActionEvent anActionEvent) {
		Editor editor = anActionEvent.getRequiredData(PlatformDataKeys.EDITOR);
		String text = editor.getDocument().getText();
		GroovyShell shell = new GroovyShell();
		shell.setProperty("actionEvent", anActionEvent);
		shell.evaluate(text);
	}
}
