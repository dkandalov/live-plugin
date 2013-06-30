package liveplugin.pluginrunner;

import liveplugin.IdeUtil;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static liveplugin.MyFileUtil.findSingleFileIn;

class ScalaPluginRunner implements PluginRunner {
	private static boolean ENABLED = IdeUtil.isOnClasspath("scala.Some");

	static final String MAIN_SCRIPT = "plugin.scala";

	private final GroovyPluginRunner groovyPluginRunner;

	public ScalaPluginRunner(ErrorReporter errorReporter, Map<String, String> environment) {
		this.groovyPluginRunner = new GroovyPluginRunner(errorReporter, environment);
	}

	@Override public boolean canRunPlugin(String pathToPluginFolder) {
		return ENABLED && findSingleFileIn(pathToPluginFolder, MAIN_SCRIPT) != null;
	}

	@Override public void runPlugin(String pathToPluginFolder, String pluginId, Map<String, ?> binding) {
		Map<String, Object> localBinding = new HashMap<String, Object>(binding);
		localBinding.put("pathToPluginFolder", pathToPluginFolder);

		URL resource = getClass().getClassLoader().getResource("liveplugin/pluginrunner/runScalaPlugin.groovy");
		assert resource != null;
		String scriptUrl = resource.toString();

		resource = getClass().getClassLoader().getResource("liveplugin/pluginrunner/");
		assert resource != null;
		String scriptFolderUrl = resource.toString();

		groovyPluginRunner.runGroovyScript(scriptUrl, scriptFolderUrl, pluginId, localBinding);
	}
}
