package liveplugin.intention;

import static liveplugin.pluginrunner.GroovyPluginRunner.GROOVY_ADD_TO_CLASSPATH_KEYWORD;

public class AddToClassPathGroovyIntention extends AddAfterImportsGroovyIntention {
	public AddToClassPathGroovyIntention() {
		super(
				GROOVY_ADD_TO_CLASSPATH_KEYWORD + "\n",
				"Inserted 'add-to-classpath'",
				"Insert 'add-to-classpath' directive"
		);
	}
}
