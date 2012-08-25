package ru.intellijeval;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nullable;

/**
 * User: dima
 * Date: 25/08/2012
 */
@State(name = "IntellijEvalSettings", storages = {@Storage(id = "other", file = "$APP_CONFIG$/intellij-eval.xml")})
public class Settings implements PersistentStateComponent<Settings> {
	public boolean justInstalled = true;
	public boolean runAllPluginsOnIDEStartup = false;

	public static Settings getInstance() {
		return ServiceManager.getService(Settings.class);
	}

	@Nullable @Override public Settings getState() {
		return this;
	}

	@Override public void loadState(Settings settings) {
		XmlSerializerUtil.copyBean(settings, this);
	}
}
