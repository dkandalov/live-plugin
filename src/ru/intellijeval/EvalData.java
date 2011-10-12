package ru.intellijeval;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;

/**
 * @author DKandalov
 */
@SuppressWarnings({"UnusedDeclaration"})
@State(
		name = "EvalData",
		storages = {@Storage(id = "main", file = "$APP_CONFIG$/intellij-eval.xml")}
)
public class EvalData implements PersistentStateComponent<EvalData> { // TODO
	private Map<String, String> pluginPaths = new HashMap<String, String>();

	public EvalData() {
		pluginPaths.put("sample plugin", "C:\\work\\zz_misc\\intellij_eval\\src\\ru\\intellijeval\\sampleplugin");
	}

	public static EvalData getInstance() {
		return ServiceManager.getService(EvalData.class);
	}

	public Map<String, String> getPluginPaths() {
		return pluginPaths;
	}

	public void setPluginPaths(Map<String, List<String>> pluginPaths) {
//		this.pluginPaths = pluginPaths;
	}

	@Override
	public EvalData getState() {
		return this;
	}

	@Override
	public void loadState(EvalData state) {
//		XmlSerializerUtil.copyBean(state, this);
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
}
