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
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import static com.intellij.openapi.application.PathManager.getLibPath;
import static com.intellij.openapi.application.PathManager.getPluginsPath;
import static com.intellij.openapi.util.text.StringUtil.join;
import static com.intellij.util.containers.ContainerUtil.map;
import static java.io.File.pathSeparator;
import static java.util.Arrays.asList;
import static liveplugin.MyFileUtil.findSingleFileIn;

/**
 * Note that this class should not be loaded unless scala libs are on classpath.
 */
class ScalaPluginRunner implements PluginRunner {
	static final String MAIN_SCRIPT = "plugin.scala";

	private static IMain interpreter;
	private static final StringWriter interpreterOutput = new StringWriter();

	private final ErrorReporter errorReporter;
	private final Map<String, String> environment;  // TODO use it


	public ScalaPluginRunner(ErrorReporter errorReporter, Map<String, String> environment) {
		this.errorReporter = errorReporter;
		this.environment = environment;

		if (interpreter == null) {
			try {
				Settings settings = new Settings();
				MutableSettings.PathSetting bootClasspath = (MutableSettings.PathSetting) settings.bootclasspath();

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
				String path =
						join(asList(compilerPath, scalaLibPath, livePluginPath), pathSeparator) +
						pathSeparator + intellijLibPath + pathSeparator + intellijPluginsPath;

				bootClasspath.append(path);
				((MutableSettings.BooleanSetting) settings.usejavacp()).tryToSetFromPropertyValue("true");

				interpreter = new IMain(settings, new PrintWriter(interpreterOutput));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	@Override public boolean canRunPlugin(String pathToPluginFolder) {
		return findSingleFileIn(pathToPluginFolder, MAIN_SCRIPT) != null;
	}

	@Override public void runPlugin(String pathToPluginFolder, String pluginId, Map<String, ?> binding) {
		interpreterOutput.getBuffer().delete(0, interpreterOutput.getBuffer().length());
		for (Map.Entry<String, ?> entry : binding.entrySet()) {
			interpreter.bindValue(entry.getKey(), entry.getValue());
		}

		File scriptFile = MyFileUtil.findSingleFileIn(pathToPluginFolder, ScalaPluginRunner.MAIN_SCRIPT);
		assert scriptFile != null;
		Results.Result result;
		try {
			result = interpreter.interpret(FileUtil.loadFile(scriptFile));
		} catch (IOException e) {
			errorReporter.addLoadingError(pluginId, "Error reading script file: " + scriptFile);
			return;
		}

		if (!(result instanceof Results.Success$)) {
			errorReporter.addRunningPluginError(pluginId, interpreterOutput.toString());
		}
	}

	private static <T> T withDefault(T defaultValue, T value) {
		return value == null ? defaultValue : value;
	}
}
