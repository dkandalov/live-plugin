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
package ru.intellijeval.git;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.PluginId;

/**
 * User: dima
 * Date: 19/08/2012
 */
public class CreateGistAction { // TODO

	public static AnAction findCreateGistAction() {

		IdeaPluginDescriptor githubPluginDescriptor = PluginManager.getPlugin(PluginId.getId("org.jetbrains.plugins.github"));
		boolean isGitPluginInstalled = githubPluginDescriptor != null;
		if (isGitPluginInstalled) {
//			actions.add(CreateGistAction.findCreateGistAction());
//			ClassLoader githubPluginClassLoader = githubPluginDescriptor.getPluginClassLoader();

/*
			try {
				String s = FileUtil.loadFile(new File(""));

				GroovyScriptEngine scriptEngine = new GroovyScriptEngine("", githubPluginClassLoader);
				Binding binding = new Binding();
				scriptEngine.run(mainScriptPath, binding);
			} catch (IOException e) {

			}
*/
		}

		return ActionManager.getInstance().getAction("Github.Create.Gist");
	}
}
