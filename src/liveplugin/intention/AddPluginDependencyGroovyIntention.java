package liveplugin.intention;

import static liveplugin.pluginrunner.GroovyPluginRunner.groovyDependsOnPluginKeyword;

public class AddPluginDependencyGroovyIntention extends AddAfterImportsGroovyIntention {
	public AddPluginDependencyGroovyIntention() {
		super(
				groovyDependsOnPluginKeyword + "\n",
				"Inserted 'depends-on-plugin'",
				"Insert 'depends-on-plugin' directive"
		);
	}
}
