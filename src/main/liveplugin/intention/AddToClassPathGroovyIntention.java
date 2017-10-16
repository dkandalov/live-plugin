package liveplugin.intention;

import static liveplugin.pluginrunner.GroovyPluginRunner.groovyAddToClasspathKeyword;

public class AddToClassPathGroovyIntention extends AddAfterImportsGroovyIntention {
	public AddToClassPathGroovyIntention() {
		super(
				groovyAddToClasspathKeyword + "\n",
				"Inserted 'add-to-classpath'",
				"Insert 'add-to-classpath' directive"
		);
	}
}
