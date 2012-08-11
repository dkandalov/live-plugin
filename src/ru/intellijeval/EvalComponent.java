package ru.intellijeval;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import ru.intellijeval.settings.SettingsForm;

import javax.swing.*;

/**
 * @author DKandalov
 */
public class EvalComponent implements ApplicationComponent, Configurable {
	public static final String COMPONENT_NAME = "EvalComponent";

	private SettingsForm settingsForm;

	@Override
	public void initComponent() {
		new PluginsToolWindow().init();
	}

	@Override
	public void disposeComponent() {
		// TODO
	}

	@Override
	@NotNull
	public String getComponentName() {
		return COMPONENT_NAME;
	}

	@Override
	public JComponent createComponent() {
		settingsForm = new SettingsForm();
		return settingsForm.getRoot();
	}

	@Override
	@Nls
	public String getDisplayName() {
		return getComponentName();
	}

	@Override
	public String getHelpTopic() {
		return settingsForm.getHelpTopic();
	}

	@Override
	public void disposeUIResources() {
		settingsForm = null;
	}

	@Override
	public void apply() throws ConfigurationException {
		settingsForm.apply();
	}

	@Override
	public boolean isModified() {
		return settingsForm.isModified();
	}

	@Override
	public void reset() {
		settingsForm.reset();
	}
}
