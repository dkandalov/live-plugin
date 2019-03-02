package liveplugin;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@State(name = "LivePluginSettings", storages = {@Storage(file = "$APP_CONFIG$/live-plugin.xml")})
public class Settings implements PersistentStateComponent<Settings> {
	public boolean justInstalled = true;
	public boolean runAllPluginsOnIDEStartup = false;

	public static Settings getInstance() {
		return ServiceManager.getService(Settings.class);
	}

	@Nullable @Override public Settings getState() {
		return this;
	}

	@Override public void loadState(@NotNull Settings settings) {
		XmlSerializerUtil.copyBean(settings, this);
	}
}
