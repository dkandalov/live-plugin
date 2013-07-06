package liveplugin.pluginrunner;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import liveplugin.MyFileUtil;
import scala.tools.nsc.Settings;
import scala.tools.nsc.interpreter.IMain;
import scala.tools.nsc.interpreter.Results;
import scala.tools.nsc.settings.MutableSettings;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.application.PathManager.getLibPath;
import static com.intellij.openapi.application.PathManager.getPluginsPath;
import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.util.containers.ContainerUtil.map;
import static java.io.File.pathSeparator;
import static java.util.Arrays.asList;
import static liveplugin.MyFileUtil.*;
import static liveplugin.pluginrunner.GroovyPluginRunner.findClasspathAdditions;

/**
 * This class should not be loaded unless scala libs are on classpath.
 */
class ScalaPluginRunner implements PluginRunner {
	private static final String MAIN_SCRIPT = "plugin.scala";
	private static final String SCALA_ADD_TO_CLASSPATH_KEYWORD = "// " + ADD_TO_CLASSPATH_KEYWORD;

	private static final StringWriter interpreterOutput = new StringWriter();
	private static final Object interpreterLock = new Object();

	private final ErrorReporter errorReporter;
	private final Map<String, String> environment;


	public ScalaPluginRunner(ErrorReporter errorReporter, Map<String, String> environment) {
		this.errorReporter = errorReporter;
		this.environment = environment;
	}

	@Override public boolean canRunPlugin(String pathToPluginFolder) {
		return findSingleFileIn(pathToPluginFolder, MAIN_SCRIPT) != null;
	}

	@Override public void runPlugin(String pathToPluginFolder, final String pluginId,
	                                Map<String, ?> binding, Function<Runnable, Void> runOnEDTCallback) {
		final File scriptFile = MyFileUtil.findSingleFileIn(pathToPluginFolder, ScalaPluginRunner.MAIN_SCRIPT);
		assert scriptFile != null;

		final IMain interpreter;
		synchronized (interpreterLock) {
			try {
				List<String> additionalPaths = findClasspathAdditions(readLines(asUrl(scriptFile)), SCALA_ADD_TO_CLASSPATH_KEYWORD, environment, new Function<String, Void>() {
					@Override public Void fun(String path) {
						errorReporter.addLoadingError(pluginId, "Couldn't find dependency '" + path + "'");
						return null;
					}
				});
				String classpath = createInterpreterClasspath(additionalPaths);
				interpreter = initInterpreter(classpath);

			} catch (Exception e) {
				errorReporter.addLoadingError("Failed to init scala interpreter", e);
				return;
			} catch (LinkageError e) {
				errorReporter.addLoadingError("Failed to init scala interpreter", e);
				return;
			}

			interpreterOutput.getBuffer().delete(0, interpreterOutput.getBuffer().length());
			for (Map.Entry<String, ?> entry : binding.entrySet()) {
				interpreter.bindValue(entry.getKey(), entry.getValue());
			}
		}

		runOnEDTCallback.fun(new Runnable() {
			@Override public void run() {
				synchronized (interpreterLock) {
					Results.Result result;
					try {
						result = interpreter.interpret(FileUtil.loadFile(scriptFile));
					} catch (Exception e) {
						errorReporter.addLoadingError(pluginId, "Error reading script file: " + scriptFile);
						return;
					}

					if (!(result instanceof Results.Success$)) {
						errorReporter.addRunningError(pluginId, interpreterOutput.toString());
					}
				}
			}
		});
	}

	private static IMain initInterpreter(String interpreterClasspath) throws ClassNotFoundException {
		Settings settings = new Settings();
		MutableSettings.PathSetting bootClasspath = (MutableSettings.PathSetting) settings.bootclasspath();
		bootClasspath.append(interpreterClasspath);

		((MutableSettings.BooleanSetting) settings.usejavacp()).tryToSetFromPropertyValue("true");

		return new IMain(settings, new PrintWriter(interpreterOutput));
	}

	private static String createInterpreterClasspath(List<String> additionalPaths) throws ClassNotFoundException {
		Function<File, String> toAbsolutePath = new Function<File, String>() {
			@Override public String fun(File it) {
				return it.getAbsolutePath();
			}
		};

		String compilerPath = PathUtil.getJarPathForClass(Class.forName("scala.tools.nsc.Interpreter"));
		String scalaLibPath = PathUtil.getJarPathForClass(Class.forName("scala.Some"));
		String intellijLibPath = join(map(withDefault(new File[0], new File(getLibPath()).listFiles()), toAbsolutePath), pathSeparator);
		String intellijPluginsPath = join(map(withDefault(new File[0], new File(getPluginsPath()).listFiles()), toAbsolutePath), pathSeparator);
		String livePluginPath = PathManager.getResourceRoot(ScalaPluginRunner.class, "/liveplugin/"); // this is only useful when running liveplugin from IDE (it's not packed into jar)
		return join(asList(compilerPath, scalaLibPath, livePluginPath, intellijLibPath, intellijPluginsPath), pathSeparator) +
				pathSeparator + join(additionalPaths, pathSeparator);
	}

	private static <T> T withDefault(T defaultValue, T value) {
		return value == null ? defaultValue : value;
	}
}
