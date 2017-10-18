package liveplugin.implementation

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.util.io.FileUtil
import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.io.xml.Xpp3Driver

class Persistence {
	static save(String id, value) {
		def xStream = new XStream(new Xpp3Driver())
		def state = value
		if (value instanceof PersistentStateComponent) {
			state = value.state
		}
		def xml = xStream.toXML(state)
		FileUtil.writeToFile(storageFile(id), xml)
	}

	static <T> T load(String id, T stateComponent = null) {
		def file = storageFile(id)
		if (!file.exists()) return null

		def xml = FileUtil.loadFile(file)
		def xStream = new XStream(new Xpp3Driver())
		def state = xStream.fromXML(xml)
		if (stateComponent != null && stateComponent instanceof PersistentStateComponent) {
			stateComponent.loadState(state)
			stateComponent as T
		} else {
			state as T
		}
	}

	static remove(String id) {
		def file = storageFile(id)
		if (file.exists()) {
			file.delete()
		}
	}

	private static File storageFile(String varName) {
		new File(PathManager.optionsPath + "/live-plugin/" + varName + ".xml")
	}
}
