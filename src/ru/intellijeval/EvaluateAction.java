package ru.intellijeval;

import static ru.intellijeval.Util.displayInConsole;
import static ru.intellijeval.Util.showInUnscrambleDialog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.codehaus.groovy.control.CompilationFailedException;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
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
	private String pluginId;
	private List<String> loadingErrors;
	private LinkedHashMap<String, Exception> evaluationExceptions;

	@Override
	public void actionPerformed(AnActionEvent event) {
		EvalData evalData = EvalData.getInstance();
		loadingErrors = new LinkedList<String>();
		evaluationExceptions = new LinkedHashMap<String, Exception>();

		for (Map.Entry<String, String> entry : evalData.getPluginPaths().entrySet()) {
			pluginId = entry.getKey();
			path = entry.getValue();

			if (mainScriptDoesNotExist()) {
				addLoadingError("Couldn't find main script");
				continue;
			}

			try {
				GroovyScriptEngine scriptEngine = new GroovyScriptEngine(path, createClassLoaderWithDependencies());
				Binding binding = new Binding();
				binding.setProperty("actionEvent", event);
				binding.setVariable("event", event);
				scriptEngine.run(MAIN_SCRIPT, binding);
			} catch (IOException e) {
				addLoadingError("Error while creating scripting engine. " + e.getMessage());
			} catch (CompilationFailedException e) {
				addLoadingError("Error while compiling script. " + e.getMessage());
			} catch (ResourceException e) {
				addEvaluationException(e);
			} catch (ScriptException e) {
				addEvaluationException(e);
			} catch (Exception e) {
				addEvaluationException(e);
			}
		}

		reportLoadingErrors(event);
		reportEvaluationExceptions(event);
	}

	private void reportLoadingErrors(AnActionEvent actionEvent) {
		StringBuilder text = new StringBuilder();
		for (String s : loadingErrors) text.append(s);
		if (text.length() > 0)
			displayInConsole(pluginId, text.toString(), ConsoleViewContentType.ERROR_OUTPUT, actionEvent.getData(PlatformDataKeys.PROJECT));
	}

	private void reportEvaluationExceptions(AnActionEvent actionEvent) {
		for (Map.Entry<String, Exception> entry : evaluationExceptions.entrySet()) {
			//noinspection ThrowableResultOfMethodCallIgnored
			showInUnscrambleDialog(entry.getValue(), actionEvent.getData(PlatformDataKeys.PROJECT));
		}
	}

	private boolean mainScriptDoesNotExist() {
		return !new File(path + "/" + MAIN_SCRIPT).exists();
	}

	private GroovyClassLoader createClassLoaderWithDependencies() {
		GroovyClassLoader classLoader = new GroovyClassLoader(this.getClass().getClassLoader());
		String fileName = path + "/" + MAIN_SCRIPT;

		try {
			BufferedReader inputStream = new BufferedReader(new InputStreamReader(new FileInputStream(fileName)));
			String line;
			while ((line = inputStream.readLine()) != null) {
				if (line.contains(CLASSPATH_PREFIX)) {
					String path = line.replace(CLASSPATH_PREFIX, "");
					List<String> filePaths = findAllFilePaths(path);
					for (String filePath : filePaths) {
						classLoader.addURL(new URL("file:/" + filePath));
					}
				}
			}
		} catch (IOException e) {
			addLoadingError("Error while looking for dependencies. Main script name: " + fileName + ". " + e.getMessage());
		}
		return classLoader;
	}

	private List<String> findAllFilePaths(String path) {
		File file = new File(path);
		if (!file.exists()) {
			loadingErrors.add("Couldn't find dependency '" + path + "'");
			return Collections.emptyList();
		}
		if (file.isFile()) return Collections.singletonList(path);
		if (file.isDirectory()) {
			List<File> allFiles = allFilesInDirectory(file);
			List<String> result = new LinkedList<String>();
			for (File aFile : allFiles) {
				result.add(aFile.getAbsolutePath());
			}
			return result;
		}
		throw new IllegalStateException();
	}

	private static List<File> allFilesInDirectory(File dir) {
		LinkedList<File> result = new LinkedList<File>();
		for (File file : dir.listFiles()) {
			if (file.isFile()) {
				result.add(file);
			} else if (file.isDirectory()) {
				result.addAll(allFilesInDirectory(dir));
			}
		}
		return result;
	}

	private void addEvaluationException(Exception e) {
		//noinspection ThrowableResultOfMethodCallIgnored
		evaluationExceptions.put(pluginId, e);
	}

	private void addLoadingError(String message) {
		loadingErrors.add("Plugin: " + pluginId + ". " + message);
	}
}
