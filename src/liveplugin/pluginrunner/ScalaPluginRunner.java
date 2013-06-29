package liveplugin.pluginrunner;

import com.intellij.openapi.actionSystem.AnActionEvent;
import liveplugin.IdeUtil;

class ScalaPluginRunner implements PluginRunner {
	private static boolean isEnabled = IdeUtil.isOnClasspath("scala.Some");
	private static final String MAIN_SCRIPT = "plugin.scala";

	private final ErrorReporter errorReporter;

	public ScalaPluginRunner(ErrorReporter errorReporter) {
		this.errorReporter = errorReporter;
	}

	@Override public boolean canRunPlugin(String pathToPluginFolder) {
		return isEnabled;
	}

	@Override public void runPlugin(String pathToPluginFolder, String pluginId, AnActionEvent event) {
		// TODO
	}
}
