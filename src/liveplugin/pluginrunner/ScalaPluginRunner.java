package liveplugin.pluginrunner;

import liveplugin.IdeUtil;

import java.util.Map;

import static liveplugin.FileUtil.findSingleFileIn;

class ScalaPluginRunner implements PluginRunner {
	private static boolean ENABLED = IdeUtil.isOnClasspath("scala.Some");

	private static final String MAIN_SCRIPT = "plugin.scala";

	private final ErrorReporter errorReporter;

	public ScalaPluginRunner(ErrorReporter errorReporter) {
		this.errorReporter = errorReporter;
	}

	@Override public boolean canRunPlugin(String pathToPluginFolder) {
		return ENABLED && findSingleFileIn(pathToPluginFolder, MAIN_SCRIPT) != null;
	}

	@Override public void runPlugin(String pathToPluginFolder, String pluginId, Map<String, ?> binding) {
		// TODO
	}
}
