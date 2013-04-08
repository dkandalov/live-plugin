<img src="https://raw.github.com/dkandalov/intellij_eval/master/toolwindow.png" alt="toolwindow" title="toolwindow" align="right" />

What is this?
=============

This is experimental IntelliJ IDEA plugin for writing plugins in Groovy at runtime<br/>
(or running Groovy code inside IntelliJ).


Why?
====
 - it should be possible to write a simple plugin without setting up new project
 - plugins source code should be easily available and editable
 - time between writing code and seeing how it works should be short


Example plugin
===============
```groovy
import com.intellij.openapi.actionSystem.AnActionEvent
import static intellijeval.PluginUtil.*

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


How to install
===============
Through IntelliJ plugin manager. Search for "eval".
(Just in case this is the [plugin page](http://plugins.jetbrains.com/plugin?pr=idea&pluginId=7173).)


How to start
=============
 - open "Plugins" tool window on the right side
 - select "helloWorld" plugin and press "ctrl + C, ctrl + E" to execute it
   ("plugin.groovy" are plugin entry points)
 - add plugin examples and experiment with them


It might be useful
==================
 - to have auto-completion by adding IDEA and IntelliJEval jars to project
   (can be done in "Settings" drop-down at the top of "Plugins" tool window).
 - install Groovy plugin
 - look at [PluginUtil](https://github.com/dkandalov/intellij_eval/blob/master/src_groovy/intellijeval/PluginUtil.groovy) class
 - get [IntelliJ source code](https://github.com/JetBrains/intellij-community)


More examples
=============
 - [Change List Size Watchdog](https://gist.github.com/dkandalov/5004622) - micro-plugin to show warning when change list size exceeds threshold
 - [Word Cloud](https://github.com/dkandalov/intellij-wordcloud) - shows world cloud for the selected item (file/package/folder)
 - [Project TreeMap View](https://github.com/dkandalov/project-treemap) - shows project structure (packages/classes) as treemap based on size of classes
 - [Template completion on "Tab"](https://gist.github.com/dkandalov/5222759) - simplistic prototype for auto-completion on tab key (in case built-in live templates are not enough)
 - [Wrap selection](https://gist.github.com/dkandalov/5129543) - micro-plugin to wrap long lines with separator
 - [Method History](https://github.com/dkandalov/history-slider-plugin) - combines built-in method history based on selection and method history based on method name
 - [Evaluate selection as Groovy](https://gist.github.com/dkandalov/5024580) - that's exactly what it does


Under the hood
===============
 - this is essentially a host plugin for other plugins
 - each plugin is evaluated with its own classloader using GroovyScriptEngine
 - it uses Groovy bundled with IntelliJ (v1.8.5 at the moment)
 - plugins are stored in "$HOME/.$INTELLIJ_VERSION/config/intellij-eval-plugins"
(on Mac "$HOME/Library/Application Support/IntelliJIdea12/intellij-eval-plugins").
You can also use standard "ctrl + shift + C" shortcut to copy file/folder path.


Similar plugins
===============
The idea of running code inside IntelliJ is not original. There are similar plugins:
 - [PMIP - Poor Mans IDE Plugin](http://plugins.intellij.net/plugin/?idea&pluginId=4571) (it's for Ruby)
 - [Remote Groovy Console](http://plugins.intellij.net/plugin/?id=5373)
 - [Script Monkey](http://plugins.intellij.net/plugin?pr=idea&pluginId=3674)
 - [Groovy Console Plugin](http://plugins.intellij.net/plugin?pr=idea&pluginId=4660)
 - [HotPlugin](http://plugins.intellij.net/plugin?pr=idea&pluginId=1020) (probably outdated)


It would be interesting
=======================
 - to have nice object tree pattern-matching API for Groovy (can be good for writing inspections/intentions to match/replace syntax tree).
 Or may be there is one and I just don't know about it.
 - use another language (e.g. Scala or Ruby).
 - go meta! Rewrite IntelliJEval as its own plugin. This is really how it was started (loads of fun with classloaders).
 The old meta-version was too broken to be released and two years later was replaced with this.
