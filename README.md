<img src="https://raw.github.com/dkandalov/intellij_eval/master/toolwindow.png" alt="toolwindow" title="toolwindow" align="right" />

What is this?
=============

This is IntelliJ IDEA plugin for writing simple plugins in Groovy<br/>
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
(Just in case this is the [plugin page](http://plugins.jetbrains.com/plugin/?idea&pluginId=7090).)


How to start
=============
 - open "Plugins" tool window on the right side
 - select "helloWorld" plugin and press "ctrl + C, ctrl + E" to execute it
   ("plugin.groovy" are plugin entry points)
 - add plugin examples and experiment with them


Might be useful to
===================
 - get auto-completion it might be useful to temporarily add IDEA and IntelliJEval jars to project
   (can be done in "Settings" drop-down at the top of "Plugins" tool window).
 - (unless you already have it) install Groovy plugin
 - look at [PluginUtil](https://github.com/dkandalov/intellij_eval/blob/master/src_groovy/intellijeval/PluginUtil.groovy) class
 - get [IntelliJ sources](https://github.com/JetBrains/intellij-community)


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
