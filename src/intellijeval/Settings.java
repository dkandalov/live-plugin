/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package intellijeval;

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
