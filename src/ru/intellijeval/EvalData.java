package ru.intellijeval;

import java.util.LinkedHashMap;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;

/**
 * @author DKandalov
 */
@SuppressWarnings({"UnusedDeclaration"})
@State(
		name = "EvalData",
		storages = {@Storage(id = "other", file = "$APP_CONFIG$/intellij-eval.xml")}
)
public class EvalData implements PersistentStateComponent<EvalData> { // TODO
	private LinkedHashMap<String, String> pluginPaths = new LinkedHashMap<String, String>();

	public static EvalData getInstance() {
		return ServiceManager.getService(EvalData.class);
	}

	public LinkedHashMap<String, String> getPluginPaths() {
		return pluginPaths;
	}

	public void setPluginPaths(LinkedHashMap<String, String> pluginPaths) {
		this.pluginPaths = pluginPaths;
	}

	@Override
	public EvalData getState() {
		return this;
	}

	@Override
	public void loadState(EvalData state) {
		XmlSerializerUtil.copyBean(state, this);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		EvalData evalData = (EvalData) o;

		return !(pluginPaths != null ? !pluginPaths.equals(evalData.pluginPaths) : evalData.pluginPaths != null);
	}

	@Override
	public int hashCode() {
		return pluginPaths != null ? pluginPaths.hashCode() : 0;
	}

	@Override
	public String toString() {
		return "EvalData{" +
				"pluginPaths=" + pluginPaths +
				'}';
	}
}
