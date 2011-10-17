package ru.intellijeval;

import javax.swing.*;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import ru.intellijeval.settings.SettingsForm;

/**
 * @author DKandalov
 */
public class EvalComponent implements ApplicationComponent, Configurable {

	private SettingsForm settingsForm;

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
	public Icon getIcon() {
		return settingsForm.getIcon();
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
