/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ru.intellijeval;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.util.GroovyScriptEngine;
import org.codehaus.groovy.control.CompilationFailedException;

import java.io.*;
import java.net.URL;
import java.util.*;

/**
 * User: dima
 * Date: 17/08/2012
 */
class Evaluator {
	private static final Logger LOG = Logger.getInstance(Evaluator.class);

	public static final String RUN_ALL_PLUGINS_ON_IDE_START = "RUN_ALL_PLUGINS_ON_IDE_START";
	private static final String CLASSPATH_PREFIX = "// add-to-classpath ";

	private final EvalErrorReporter errorReporter;

	public Evaluator(EvalErrorReporter errorReporter) {
		this.errorReporter = errorReporter;
	}

	public void doEval(String pluginId, String path, AnActionEvent event) {

		String mainScriptPath = findMainScriptIn(path);
		if (mainScriptPath == null) {
			errorReporter.addLoadingError(pluginId, "Couldn't find " + EvalComponent.MAIN_SCRIPT);
			return;
		}

		try {

			GroovyClassLoader classLoader = createClassLoaderWithDependencies(mainScriptPath, pluginId, System.getenv());
			GroovyScriptEngine scriptEngine = new GroovyScriptEngine("file:///" + path, classLoader);
			Binding binding = new Binding();
			binding.setVariable("event", event);
			binding.setVariable("project", event.getProject());
			binding.setVariable("isIdeStartup", event.getPlace().equals(RUN_ALL_PLUGINS_ON_IDE_START));
			scriptEngine.run("file:///" + mainScriptPath, binding);

		} catch (IOException e) {
			errorReporter.addLoadingError(pluginId, "Error while creating scripting engine. " + e.getMessage());
		} catch (CompilationFailedException e) {
			errorReporter.addLoadingError(pluginId, "Error while compiling script. " + e.getMessage());
		} catch (VerifyError e) {
			errorReporter.addLoadingError(pluginId, "Error while compiling script. " + e.getMessage());
		} catch (Exception e) {
			errorReporter.addEvaluationException(pluginId, e);
		}
	}

	private String findMainScriptIn(String path) {
		List<File> files = allFilesInDirectory(new File(path));
		List<File> result = new ArrayList<File>();
		for (File file : files) {
			if (EvalComponent.MAIN_SCRIPT.equals(file.getName())) {
				result.add(file);
			}
		}
		if (result.size() == 0) return null;
		else if (result.size() == 1) return result.get(0).getAbsolutePath();
		else throw new IllegalStateException("Found several " + EvalComponent.MAIN_SCRIPT + " files under " + path);
	}

	private GroovyClassLoader createClassLoaderWithDependencies(String mainScriptPath, String pluginId, Map<String, String> environment) {
		GroovyClassLoader classLoader = new GroovyClassLoader(this.getClass().getClassLoader());

		try {
			classLoader.addURL(new URL("file:///" + new File(mainScriptPath).getParent()));
			classLoader.addClasspath(new File(mainScriptPath).getParent());

			BufferedReader inputStream = new BufferedReader(new InputStreamReader(new FileInputStream(mainScriptPath)));
			String line;
			while ((line = inputStream.readLine()) != null) {
				if (line.contains(CLASSPATH_PREFIX)) {
					String path = line.replace(CLASSPATH_PREFIX, "");

					path = inlineEnvironmentVariables(path, environment);

					List<String> filePaths = collectAllFilesPaths(path);
					if (filePaths.isEmpty()) {
						errorReporter.addLoadingError(pluginId, "Couldn't find dependency '" + path + "'");
					}
					for (String filePath : filePaths) {
						classLoader.addClasspath(filePath);
					}
				}
			}
		} catch (IOException e) {
			errorReporter.addLoadingError(pluginId, "Error while looking for dependencies. Main script: " + mainScriptPath + ". " + e.getMessage());
		}
		return classLoader;
	}

	private static String inlineEnvironmentVariables(String s, Map<String, String> environment) {
		boolean wasModified = false;
		for (Map.Entry<String, String> entry : environment.entrySet()) {
			s = s.replace("$" + entry.getKey(), entry.getValue());
			wasModified = true;
		}
		if (wasModified) LOG.info("Path with env variables: " + s);
		return s;
	}

	private List<String> collectAllFilesPaths(String path) {
		File file = new File(path);
		if (!file.exists()) {
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
		File[] files = dir.listFiles();
		if (files == null) return result;

		for (File file : files) {
			if (file.isFile()) {
				result.add(file);
			} else if (file.isDirectory()) {
				result.addAll(allFilesInDirectory(file));
			}
		}
		return result;
	}

}
