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
