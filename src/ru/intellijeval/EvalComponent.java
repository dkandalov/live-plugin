package ru.intellijeval;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.components.ApplicationComponent;

/**
 * @author DKandalov
 */
public class EvalComponent implements ApplicationComponent {
	@Override
	public void initComponent() {
		// TODO
	}

	@Override
	public void disposeComponent() {
		// TODO
	}

	@Override
	@NotNull
	public String getComponentName() {
		return "EvalComponent";
	}
}
