<img src="https://raw.github.com/dkandalov/live-plugin/master/toolwindow.png" alt="toolwindow" title="toolwindow" align="right" />

### What is this?

This is [IntelliJ](https://github.com/JetBrains/intellij-community) plugin for writing
IDE plugins at runtime (or running any code inside IntelliJ).<br/>
It currently uses [Groovy](http://groovy.codehaus.org/) as a main language and
has experimental support for [Scala](http://www.scala-lang.org/) and [Clojure](http://clojure.org/).


### Why?
 - to make writing plugins easier. There is no need to set up and configure a separate project.
 - faster feedback loop. There is no need to start new IDE instance to run plugin.
   If you change plugin code, there is no need to restart IDE.
 - great goodness of customized IDE. In a way even Excel can be "customized" at runtime with VB script.
 This is an attempt to fix this and empower yourself with easy-to-extend IDE.


### Example plugin
```groovy
import com.intellij.openapi.actionSystem.AnActionEvent
import static liveplugin.PluginUtil.*

// This action inserts new line above current line.
// It's a follow-up for these posts:
//   http://martinfowler.com/bliki/InternalReprogrammability.html
//   http://nealford.com/memeagora/2013/01/22/why_everyone_eventually_hates_maven.html
// Note that there is "Start New Line Before Current" action (ctrl + alt + enter) which does almost the same thing.

registerAction("InsertNewLineAbove", "alt shift ENTER") { AnActionEvent event ->
	runDocumentWriteAction(event.project) {
		currentEditorIn(event.project).with {
			def offset = caretModel.offset
			def currentLine = caretModel.logicalPosition.line
			def lineStartOffset = document.getLineStartOffset(currentLine)

			document.insertString(lineStartOffset, "\n")
			caretModel.moveToOffset(offset + 1)
		}
	}
}
show("Loaded 'InsertNewLineAbove' action<br/>Use 'Alt+Shift+Enter' to run it")
```

See also [Scala example](https://gist.github.com/dkandalov/5921340) and [Clojure example](https://gist.github.com/dkandalov/5943754).


### How to install
Through IntelliJ plugin manager. Search for "liveplugin".
(Just in case this is the [plugin page](http://plugins.jetbrains.com/plugin?pr=idea&pluginId=7173).) TODO


### How to start
 - open "Plugins" tool window on the right side
 - select "helloWorld" plugin and press "alt + C, alt + E" to execute it
   ("plugin.groovy" are plugin entry points)
 - add plugin examples and experiment with them


### "Advanced" usage
 - it helps to have [JetGroovy](http://plugins.jetbrains.com/plugin/1524?pr=idea) plugin installed (available only for IntelliJ IDEA)
 - you can get auto-completion in plugins code by adding IDEA and LivePlugin jars to project
   (in "Settings" drop-down at the top of "Plugins" tool window).
 - check [PluginUtil](https://github.com/dkandalov/live-plugin/blob/master/src_groovy/liveplugin/PluginUtil.groovy) class.
 Even if you don't want to use it, it might be a good place to see how to interact with IntelliJ API.
 - get [IntelliJ source code](https://github.com/JetBrains/intellij-community), look how some feature is implemented, rip it off


### More examples
 - [Symbolize keywords](https://gist.github.com/dkandalov/5553999) - collapses Java keywords into shorter symbols
 - [Change List Size Watchdog](https://gist.github.com/dkandalov/5004622) - micro-plugin to show warning when change list size exceeds threshold
 - [Template completion on "Tab"](https://gist.github.com/dkandalov/5222759) - simplistic prototype for auto-completion on tab key (in case built-in live templates are not enough)
 - [No copy-paste](https://gist.github.com/dkandalov/5430282) - disables copy/paste actions
 - [Wrap selection](https://gist.github.com/dkandalov/5129543) - micro-plugin to wrap long lines with separator
 - [Wrap selected text to column width](https://gist.github.com/dkandalov/5557393) - copy of this plugin https://github.com/abrookins/WrapToColumn
 - [Create .jar patch file for current change list](https://gist.github.com/dkandalov/5502872) - that's what it does
 - [Remove getters/setters](https://gist.github.com/dkandalov/5476562) - removes all setters or getters in a class
 - [ISO DateTime / Epoch timestamp converter](https://gist.github.com/xhanin/4948901) - converts Epoch time to/from ISO format
 - [Word Cloud](https://github.com/dkandalov/intellij-wordcloud) - shows world cloud for the selected item (file/package/folder)
 - [Project TreeMap View](https://github.com/dkandalov/project-treemap) - shows project structure (packages/classes) as treemap based on size of classes
 - [Method History](https://github.com/dkandalov/history-slider-plugin) - combines built-in method history based on selection and method history based on method name
 - [Evaluate selection as Groovy](https://gist.github.com/dkandalov/5024580) - that's exactly what it does


### How this plugin works?
It just evaluates code inside JVM, like this:
```java
GroovyScriptEngine scriptEngine = new GroovyScriptEngine(pluginFolderUrl, classLoader);
scriptEngine.run(mainScriptUrl, createGroovyBinding(binding));
```
 - each plugin is evaluated with its own classloader
 - it uses Groovy bundled with IntelliJ
 - plugins are stored in "$HOME/.$INTELLIJ_VERSION/config/live-plugins"
(on Mac "$HOME/Library/Application Support/IntelliJIdea12/live-plugins").
You can also use standard "ctrl + shift + C" shortcut to copy file/folder path.


### Similar plugins
The idea of running code inside IntelliJ is not original. There are similar plugins (although I wasn't too happy with them):
 - [PMIP - Poor Mans IDE Plugin](http://plugins.intellij.net/plugin/?idea&pluginId=4571) (for Ruby)
 - [Remote Groovy Console](http://plugins.intellij.net/plugin/?id=5373)
 - [Script Monkey](http://plugins.intellij.net/plugin?pr=idea&pluginId=3674)
 - [Groovy Console Plugin](http://plugins.intellij.net/plugin?pr=idea&pluginId=4660)
 - [HotPlugin](http://plugins.intellij.net/plugin?pr=idea&pluginId=1020) (probably outdated)


### It would be interesting
 - try writing a language plugin
 - to have nice object tree pattern-matching API for Groovy (can be good for writing inspections/intentions to match/replace syntax tree).
 - more languages, e.g. Ruby, Kotlin or Java.
 - go meta! Rewrite this plugin as its own plugin. This is really how it was started (loads of fun with classloaders).
 The old meta-version was too broken to be released and two years later was replaced with this.

### If you want to contribute
That's great!
But please be aware this is a proof-of-concept project, don't expect to see great code.
I could write a blog post about extendable IDEs but probably not many people would read it
so this is kind of "blog with working code" approach.

 - use download_libs.rb to get dependencies
 - open project in IntelliJ IDEA (you probably will have to configure IntelliJ SDK)
 - packages are structured in attempt to reflect UI structure

