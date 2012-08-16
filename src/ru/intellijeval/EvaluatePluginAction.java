package ru.intellijeval;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;

/**
 * User: dima
 * Date: 16/08/2012
 */
public class EvaluatePluginAction extends AnAction {
	public EvaluatePluginAction() {
		super("Run plugin", "Run current plugin", Util.EVAL_ICON);
	}

	@Override public void actionPerformed(AnActionEvent e) {
		// TODO
	}
}
