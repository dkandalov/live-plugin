package liveplugin.pluginrunner;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import liveplugin.MyFileUtil;
import scala.Some;
import scala.package$;
import scala.tools.nsc.Settings;
import scala.tools.nsc.interpreter.IMain;
import scala.tools.nsc.interpreter.Results;
import scala.tools.nsc.settings.MutableSettings;
import scala.xml.Null;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.intellij.openapi.application.PathManager.getLibPath;
import static com.intellij.openapi.application.PathManager.getPluginsPath;
import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.util.containers.ContainerUtil.flatten;
import static com.intellij.util.containers.ContainerUtil.map;
import static com.intellij.util.containers.ContainerUtilRt.newArrayList;
import static java.io.File.pathSeparator;
import static java.util.Arrays.asList;
import static liveplugin.MyFileUtil.*;
import static liveplugin.pluginrunner.PluginRunner.ClasspathAddition.*;

/**
 * This class should not be loaded unless scala libs are on classpath.
 */
public class ScalaPluginRunner implements PluginRunner {
	private static final String SCALA_DEPENDS_ON_PLUGIN_KEYWORD = "// " + DEPENDS_ON_PLUGIN_KEYWORD;
	public static final String MAIN_SCRIPT = "plugin.scala";
	private static final String SCALA_ADD_TO_CLASSPATH_KEYWORD = "// " + ADD_TO_CLASSPATH_KEYWORD;
	private static final StringWriter interpreterOutput = new StringWriter();
	private static final Object interpreterLock = new Object();

	private final ErrorReporter errorReporter;
	private final Map<String, String> environment;


	public ScalaPluginRunner(ErrorReporter errorReporter, Map<String, String> environment) {
		this.errorReporter = errorReporter;
		this.environment = environment;
	}

	private static IMain initInterpreter(String interpreterClasspath, ClassLoader parentClassLoader) throws ClassNotFoundException {
		Settings settings = new Settings();
		MutableSettings.PathSetting bootClasspath = (MutableSettings.PathSetting) settings.bootclasspath();
		bootClasspath.append(interpreterClasspath);

		settings.explicitParentLoader_$eq(new Some<>(parentClassLoader));

		((MutableSettings.BooleanSetting) settings.usejavacp()).tryToSetFromPropertyValue("true");

		return new IMain(settings, new PrintWriter(interpreterOutput));
	}

	private static String createInterpreterClasspath(List<String> additionalPaths) throws ClassNotFoundException {
		Function<File, String> toAbsolutePath = it -> it.getAbsolutePath();
		Function<File, Collection<File>> findPluginJars = pluginPath -> {
			if (pluginPath.isFile()) {
				return newArrayList(pluginPath);
			} else {
				return newArrayList(withDefault(new File[0], new File(pluginPath, "lib").listFiles((file, fileName) -> fileName.endsWith(".jar") || fileName.endsWith(".zip"))));
			}
		};

		String compilerPath = PathUtil.getJarPathForClass(Class.forName("scala.tools.nsc.Interpreter"));
		String scalaLibPath = PathUtil.getJarPathForClass(Class.forName("scala.Some"));
		String intellijLibPath = join(map(withDefault(new File[0], new File(getLibPath()).listFiles()), toAbsolutePath), pathSeparator);
		String allNonCorePluginsPath = join(map(flatten(map(withDefault(new File[0], new File(getPluginsPath()).listFiles()), findPluginJars)), toAbsolutePath), pathSeparator);
		String livePluginPath = PathManager.getResourceRoot(ScalaPluginRunner.class, "/liveplugin/"); // this is only useful when running liveplugin from IDE (it's not packed into jar)
		return join(asList(compilerPath, scalaLibPath, livePluginPath, intellijLibPath, allNonCorePluginsPath), pathSeparator) +
				pathSeparator + join(additionalPaths, pathSeparator);
	}

	private static <T> T withDefault(T defaultValue, T value) {
		return value == null ? defaultValue : value;
	}

	@Override public boolean canRunPlugin(String pathToPluginFolder) {
		return findScriptFileIn(pathToPluginFolder, MAIN_SCRIPT) != null;
	}

	@Override public void runPlugin(String pathToPluginFolder, final String pluginId,
	                                Map<String, ?> binding, Function<Runnable, Void> runOnEDTCallback) {
		final File scriptFile = MyFileUtil.findScriptFileIn(pathToPluginFolder, ScalaPluginRunner.MAIN_SCRIPT);
		assert scriptFile != null;

		final IMain interpreter;
		synchronized (interpreterLock) {
			try {
				environment.put("PLUGIN_PATH", pathToPluginFolder);

				List<String> dependentPlugins = findPluginDependencies(readLines(asUrl(scriptFile)), SCALA_DEPENDS_ON_PLUGIN_KEYWORD);
				List<String> additionalPaths = findClasspathAdditions(readLines(asUrl(scriptFile)), SCALA_ADD_TO_CLASSPATH_KEYWORD, environment, path -> {
					errorReporter.addLoadingError(pluginId, "Couldn't find dependency '" + path + "'");
					return null;
				});
				String classpath = createInterpreterClasspath(additionalPaths);
				ClassLoader parentClassLoader = createParentClassLoader(dependentPlugins, pluginId, errorReporter);
				interpreter = initInterpreter(classpath, parentClassLoader);

			} catch (Exception | LinkageError e) {
				errorReporter.addLoadingError("Failed to init scala interpreter", e);
				return;
			}

			interpreterOutput.getBuffer().delete(0, interpreterOutput.getBuffer().length());
			for (Map.Entry<String, ?> entry : binding.entrySet()) {
				String key = entry.getKey();
				Object value = entry.getValue();
				String valueClassName = value == null ? Null.class.getCanonicalName() : value.getClass().getCanonicalName();
				interpreter.bind(key, valueClassName, value, package$.MODULE$.List().empty());
			}
		}

		runOnEDTCallback.fun(() -> {
			synchronized (interpreterLock) {
				Results.Result result;
				try {
					result = interpreter.interpret(FileUtil.loadFile(scriptFile));
				} catch (LinkageError e) {
					errorReporter.addLoadingError(pluginId, "Error linking script file: " + scriptFile);
					return;
				} catch (Exception e) {
					errorReporter.addLoadingError(pluginId, "Error reading script file: " + scriptFile);
					return;
				}

				if (!(result instanceof Results.Success$)) {
					errorReporter.addRunningError(pluginId, interpreterOutput.toString());
				}
			}
		});
	}

	@Override public String scriptName() {
		return MAIN_SCRIPT;
	}
}
