package liveplugin.intention;

import static liveplugin.pluginrunner.GroovyPluginRunner.GROOVY_DEPENDS_ON_PLUGIN_KEYWORD;

public class AddPluginDependencyGroovyIntention extends AddAfterImportsGroovyIntention {
	public AddPluginDependencyGroovyIntention() {
		super(
				GROOVY_DEPENDS_ON_PLUGIN_KEYWORD + "\n",
				"Inserted 'depends-on-plugin'",
				"Insert 'depends-on-plugin' directive"
		);
	}
}
