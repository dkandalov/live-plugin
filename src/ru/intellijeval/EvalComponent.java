package ru.intellijeval;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import ru.intellijeval.toolwindow.PluginsToolWindow;

import javax.swing.*;

/**
 * @author DKandalov
 */
public class EvalComponent implements ApplicationComponent, Configurable {
	public static final String COMPONENT_NAME = "EvalComponent";

	@Override
	public void initComponent() {
		new PluginsToolWindow().init();
	}

	@Override
	public void disposeComponent() {
	}

	@Override
	@NotNull
	public String getComponentName() {
		return COMPONENT_NAME;
	}

	@Override
	public JComponent createComponent() {
		return null;
	}

	@Override
	@Nls
	public String getDisplayName() {
		return getComponentName();
	}

	@Override
	public String getHelpTopic() {
		return null;
	}

	@Override
	public void disposeUIResources() {
	}

	@Override
	public void apply() throws ConfigurationException {
	}

	@Override
	public boolean isModified() {
		return false;
	}

	@Override
	public void reset() {
	}
}
