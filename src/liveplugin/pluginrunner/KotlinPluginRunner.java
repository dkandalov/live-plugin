package liveplugin.pluginrunner;

import com.intellij.util.Function;
import liveplugin.pluginrunner.kotlin.EmbeddedCompilerRunnerKt;

import java.util.Map;

import static liveplugin.MyFileUtil.findScriptFileIn;

public class KotlinPluginRunner implements PluginRunner {
	public static final String MAIN_SCRIPT = "plugin.kts";

	private final ErrorReporter errorReporter;
	private final Map<String, String> environment;


	public KotlinPluginRunner(ErrorReporter errorReporter, Map<String, String> environment) {
		this.errorReporter = errorReporter;
		this.environment = environment;
	}

	@Override public String scriptName() {
		return MAIN_SCRIPT;
	}

	@Override public boolean canRunPlugin(String pathToPluginFolder) {
		return findScriptFileIn(pathToPluginFolder, MAIN_SCRIPT) != null;
	}

	@Override
	public void runPlugin(String pathToPluginFolder, String pluginId, Map<String, ?> binding, Function<Runnable, Void> runOnEDTCallback) {
		EmbeddedCompilerRunnerKt.runPlugin(
				pathToPluginFolder,
				pluginId,
				binding,
				runOnEDTCallback,
				errorReporter,
				environment
		);
	}
}
