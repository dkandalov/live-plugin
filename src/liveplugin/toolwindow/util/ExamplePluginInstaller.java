package liveplugin.toolwindow.util;

import com.intellij.openapi.util.Pair;
import liveplugin.LivePluginAppComponent;

import java.io.IOException;
import java.util.List;

public class ExamplePluginInstaller {
	private final String pluginPath;
	private final List<String> filePaths;
	private final String pluginId;

	public ExamplePluginInstaller(String pluginPath, List<String> filePaths) {
		this.pluginPath = pluginPath;
		this.filePaths = filePaths;
		this.pluginId = extractPluginIdFrom(pluginPath);
	}

	public void installPlugin(Listener listener) {
		for (String relativeFilePath : filePaths) {
			try {

				String text = LivePluginAppComponent.readSampleScriptFile(pluginPath, relativeFilePath);
				Pair<String, String> pair = splitIntoPathAndFileName(LivePluginAppComponent.pluginsRootPath() + "/" + pluginId + "/" + relativeFilePath);
				PluginsIO.createFile(pair.first, pair.second, text);

			} catch (IOException e) {
				listener.onException(e, pluginPath);
			}
		}
	}

	private static Pair<String, String> splitIntoPathAndFileName(String filePath) {
		int index = filePath.lastIndexOf("/");
		return new Pair<String, String>(filePath.substring(0, index), filePath.substring(index + 1));
	}

	public static String extractPluginIdFrom(String pluginPath) {
		String[] split = pluginPath.split("/");
		return split[split.length - 1];
	}

	public interface Listener {
		void onException(Exception e, String pluginPath);
	}
}
