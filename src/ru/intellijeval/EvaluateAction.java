package ru.intellijeval;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Map;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.util.GroovyScriptEngine;
import groovy.util.ResourceException;
import groovy.util.ScriptException;

/**
 * @author DKandalov
 */
public class EvaluateAction extends AnAction {

	private static final String MAIN_SCRIPT = "plugin.groovy";
	private static final String CLASSPATH_PREFIX = "//-- classpath: ";
	private String path;

	@Override
	public void actionPerformed(AnActionEvent anActionEvent) {
		EvalData evalData = EvalData.getInstance();

		for (Map.Entry<String, String> entry : evalData.getPluginPaths().entrySet()) {
			path = entry.getValue();

			try {
				if (mainScriptDoesNotExist()) {
					continue; // TODO
				}

				GroovyScriptEngine scriptEngine = new GroovyScriptEngine(path, createClassLoaderWithDependencies());
				Binding binding = new Binding();
				binding.setProperty("actionEvent", anActionEvent);
				scriptEngine.run(MAIN_SCRIPT, binding);

			} catch (IOException e) {
				e.printStackTrace();  // TODO
			} catch (ResourceException e) {
				e.printStackTrace();  // TODO
			} catch (ScriptException e) {
				e.printStackTrace();  // TODO
			}
		}
	}

	private boolean mainScriptDoesNotExist() {
		return !new File(path + "/" + MAIN_SCRIPT).exists();
	}

	private GroovyClassLoader createClassLoaderWithDependencies() throws IOException {
		GroovyClassLoader classLoader = new GroovyClassLoader(this.getClass().getClassLoader());

		BufferedReader inputStream = new BufferedReader(new InputStreamReader(new FileInputStream(path + "/" + MAIN_SCRIPT)));
		String line;
		while ((line = inputStream.readLine()) != null) {
			if (line.contains(CLASSPATH_PREFIX)) {
				classLoader.addURL(new URL("file:/" + line.replace(CLASSPATH_PREFIX, "")));
			}
		}

		return classLoader;
	}
}
