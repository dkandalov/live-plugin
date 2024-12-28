package liveplugin.implementation

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.impl.AsyncDataContext
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.NotNull

class MapDataContext implements DataContext, AsyncDataContext {
	private final Map map

	MapDataContext(Map map = [:]) {
		this.map = map
	}

	@Override Object getData(@NonNls String dataId) {
		map.get(dataId)
	}

	@Override <T> T getData(@NotNull DataKey<T> key) {
		map.get(dataId)
	}

	MapDataContext put(@NotNull String key, Object value) {
		map.put(key, value)
		this
	}
}
