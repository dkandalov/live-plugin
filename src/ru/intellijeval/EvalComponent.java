package ru.intellijeval;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import ru.intellijeval.toolwindow.PluginsToolWindow;

import javax.swing.*;
import java.io.File;
import java.io.FileFilter;
import java.util.HashMap;
import java.util.Map;

/**
 * @author DKandalov
 */
public class EvalComponent implements ApplicationComponent, Configurable {
	public static final String COMPONENT_NAME = "EvalComponent";

	public static String pluginsRootPath() {
		return PathManager.getPluginsPath() + "/intellij-eval-plugins/";
	}

	public static Map<String, String> pluginToPathMap() {
		File[] files = new File(pluginsRootPath()).listFiles(new FileFilter() {
			@Override public boolean accept(File file) {
				return file.isDirectory();
			}
		});

		HashMap<String, String> result = new HashMap<String, String>();
		for (File file : files) {
			result.put(file.getName(), file.getAbsolutePath());
		}
		return result;
	}

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
