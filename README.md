[![Build Status](https://travis-ci.org/dkandalov/live-plugin.svg?branch=master)](https://travis-ci.org/dkandalov/live-plugin)

### LivePlugin

This is a plugin for [IntelliJ](https://github.com/JetBrains/intellij-community) IDEs to write plugins at runtime
using [Groovy](http://groovy.codehaus.org/) and [Kotlin](http://kotlinlang.org).
To install search for "LivePlugin" in `IDE Preferences -> Plugins -> Marketplace`.
See also [plugin repository page](http://plugins.jetbrains.com/plugin/7282).


<img src="https://raw.github.com/dkandalov/live-plugin/master/screenshots/live-plugin-demo.gif" alt="demo" title="demo" align="center"/>


### Why?
There is great [Internal Reprogrammability blog post](http://martinfowler.com/bliki/InternalReprogrammability.html)
on this topic by Martin Fowler. 

The reasons LivePlugin exists are along the same lines:
 - **minimal setup to start writing plugin**.
   LivePlugins can always be edited/executed in LivePlugin toolwindow regardless of the current project in IDE.
   This takes much less effort than creating new project configured for plugin development.
 - **fast feedback loop**.
   LivePlugins are run in the same JVM instance, so there is no need to restart IDE.
   Typical plugin development involves starting new instance of IDE and restarting it on most code changes.
   (There is a caveat that some parts of IDE API are not designed to be reconfigured without JVM restart.
   This is unfortunate and will hopefully change sometime in the future.)
 - **customizable IDE**.
   Majority of the development tools are difficult to customize.
   This plugin is an attempt to improve the situation.

Practical use cases:
 - prototyping of IntelliJ plugins
 - experimenting with IntelliJ API
 - project-specific workflow automation
 - integrating shell scripts with IDE
 

### Plugin Examples
Hello world:
```groovy
import static liveplugin.PluginUtil.show
show("Hello world") // shows balloon message with "Hello world" text
```
Insert New Line Above Action:
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


### How to start writing plugins
 - open `Plugins` tool window
 - select one of the plugin entries in the panel<br/>
   (entries are folders, and `plugin.groovy` are startup scripts for plugins)
 - click `Run` icon to execute plugin (or use keyboard shortcuts `alt+C, alt+E` or `ctrl+shift+L`)

If the above worked fine:
 - modify `plugin.groovy` and rerun plugin to see results
 - add built-in plugin examples and experiment with them 
   (in `Plugins` toolwindow header `+ button -> Examples`) 

If something doesn't work, [report an issue](https://github.com/dkandalov/live-plugin/issues).

(To use `alt+...` shortcuts on OSX you might need a workaround, please see
[this wiki page](https://github.com/dkandalov/live-plugin/wiki/Alt-keyboard-shortcuts-on-osx)
.)


### The main idea
LivePlugin basically runs Groovy or Kotlin code in JVM. Conceptually it's quite simple:
```java
ClassLoader classLoader = createClassLoader(ideClassloader, ...);
GroovyScriptEngine scriptEngine = new GroovyScriptEngine(pluginFolderUrl, classLoader);
scriptEngine.run(mainScriptUrl, createGroovyBinding(binding));
```
This means that your code is executed in the same environment as IDE internal code.
You can use any internal API and observe/change state of any object inside IDE.
There are some limitations of course, like `final` fields and complex APIs not designed to be re-initialized. 

To simplify usage of IntelliJ API for practical purposes some parts of IntelliJ API are wrapped in 
[PluginUtil class](https://github.com/dkandalov/live-plugin/blob/master/src/plugin-util-groovy/liveplugin/PluginUtil.groovy).
This is essentially a layer on top standard IntelliJ API. 
If you find yourself writing interesting IDE scripts, feel free to create pull request or send a gist
to include your code into `PluginUtil`. This is experimental API and there is no intention to keep it minimal.
`PluginUtil` is not required though and you can always use IntelliJ classes directly.
  
Also note that:
 - plugins are evaluated with new classloader on each run
 - plugins are stored in `$HOME/.$INTELLIJ_VERSION/config/live-plugins`
(on Mac `$HOME/Library/Application Support/IntelliJIdea15/live-plugins`)
Note that you can use `ctrl+shift+C` shortcut to copy file/folder path.
 - if available, Groovy library bundled with IDE is used


### Misc tips
 - if your plugins are stable enough, you can enable `Settings -> Run All Live Plugins on IDE Startup` option.
 If some of the plugins are not meant to be executed at startup, add `if (isIdeStartup) return` statement at the top.
 - it helps to have [JetGroovy](http://plugins.jetbrains.com/plugin/1524?pr=idea) plugin installed (only available in IDEs with Java support)
 - you can get auto-completion and code navigation in plugins code
	- install/enable Groovy plugin
    - `Plugin toolwindow -> Settings -> Add LivePlugin and IDE Jars to Project`<br/> 
    (adding jars unrelated to your project is a hack but there seems to be no major problems with it)
 - it helps to be familiar with IntelliJ API
	 - get and explore [IntelliJ source code](https://github.com/JetBrains/intellij-community)
	 (e.g. one strategy is to search for some text you can see in IDE UI and try to figure out how it's connected the code which does actual work)
     - read [jetbrains plugin development page](http://www.jetbrains.org/intellij/sdk/docs/)
     - [PluginUtil](https://github.com/dkandalov/live-plugin/blob/master/src/plugin-util-groovy/liveplugin/PluginUtil.groovy)
       might be a good start point to explore IntelliJ API
 - when plugin seems to be big enough, you can move it to proper plugin project and still use live plugin
 See [liveplugin as an entry point for standard plugins](https://github.com/dkandalov/live-plugin/wiki/Liveplugin-as-an-entry-point-for-standard-plugins).


### More examples
 - [intellij-emacs](https://github.com/kenfox/intellij-emacs) - macros for making IntelliJ more friendly to emacs users (see also [blog post](http://spin.atomicobject.com/2014/08/07/intellij-emacs/))
 - [Simplistic "compile and run haskell" action](https://gist.github.com/dkandalov/11051113) - obviously this can be done for other languages/environments
 - [Google quick search popup](https://gist.github.com/dkandalov/277800d12ecbfc533fcd) - prototype of google popup search mini-plugin
 - [Scripting a macros](https://github.com/dkandalov/live-plugin/wiki/Scripting-a-macros) - example of finding and invoking built-in actions
 - [Console filter/transform example](https://github.com/dkandalov/live-plugin/wiki/Console-filtering) - example of filtering and changing console output
 - [VCS update listener example](https://gist.github.com/dkandalov/8840509) - example of adding callback on VCS update
 - [Find class dependencies](https://gist.github.com/dkandalov/6976133) - simple action to find all class dependencies within current project
 - [Module transitive dependencies](https://gist.github.com/dkandalov/80d8d4f71bef54290a71) - finds all transitive dependencies for modules in IDEA project
 - [Show text diff](https://gist.github.com/dkandalov/6728950) - really lame example of opening IntelliJ text diff window (please don't use it!)
 - [Find all recursive methods in project (for Java)](https://gist.github.com/dkandalov/7248184) - quick plugin as a follow up for this [talk](http://skillsmatter.com/podcast/nosql/using-graphs-for-source-code-analysis)
 - [Watching projects open/close events](https://gist.github.com/dkandalov/6427087) - an example of reloadable project listener
 - [Minimalistic view for java code](https://gist.github.com/dkandalov/708664109a37c3c0ff15) - collapses most of Java keywords and types leaving only variable names
 - [Symbolize keywords](https://gist.github.com/dkandalov/5553999) - collapses Java keywords into shorter symbols
 - [Change List Size Watchdog](https://gist.github.com/dkandalov/5004622) - micro-plugin to show warning when change list size exceeds threshold (see also [Limited WIP plugin](https://github.com/dkandalov/limited-wip))
 - [Template completion on "Tab"](https://gist.github.com/dkandalov/5222759) - simplistic prototype for auto-completion on tab key (in case built-in live templates are not enough)
 - [Completion contributor example](https://gist.github.com/dkandalov/5977888) - only gives an idea which part of IntelliJ API to use
 - [Google auto-completion contributor example](https://github.com/dkandalov/live-plugin/wiki/Google-auto-complete) - same as above but with google search plugged in
 - [Add custom search example](https://gist.github.com/dkandalov/5956923) - only gives an idea which part of IntelliJ API to use
 - [Get files from last commits example](https://gist.github.com/dkandalov/5984577) - gets VirtualFiles from several last commits
 - [Show PSI view dialog](https://gist.github.com/dkandalov/5979943) - one-liner to show PSI viewer dialog. Normally it's only enabled in plugin projects.
 - [Simplistic "generify return type"](https://gist.github.com/dkandalov/5992191) - attempt to pattern-match PSI tree
 - [No copy-paste](https://gist.github.com/dkandalov/5430282) - disables copy/paste actions
 - [Text munging actions](https://gist.github.com/dkandalov/34daca651fb3fbb9b33f) - simple actions on text (sort, unique, keep/delete lines)
 - [Wrap selection](https://gist.github.com/dkandalov/5129543) - micro-plugin to wrap long lines with separator
 - [Wrap selected text to column width](https://gist.github.com/dkandalov/5557393) - copy of this plugin https://github.com/abrookins/WrapToColumn
 - [Create .jar patch file for current change list](https://gist.github.com/dkandalov/5502872) - that's what it does
 - [Create .jar patch file for specified favorites list](https://gist.github.com/chanshuikay/9850327817fbedceba75) - similar to the above mini-plugin
 - [Remove getters/setters](https://gist.github.com/dkandalov/5476562) - removes all setters or getters in a class
 - [ISO DateTime / Epoch timestamp converter](https://gist.github.com/xhanin/4948901) - converts Epoch time to/from ISO format
 - [Make cursor move in circle](https://gist.github.com/dkandalov/11326385) - definitely not practical but gives an idea about threading
 - [Word Cloud](https://github.com/dkandalov/intellij-wordcloud) - shows world cloud for the selected item (file/package/folder)
 - [Project TreeMap View](https://github.com/dkandalov/project-treemap) - shows project structure (packages/classes) as treemap based on size of classes
 - [Method History](https://github.com/dkandalov/history-slider-plugin) - combines built-in method history based on selection and method history based on method name
 - [Evaluate selection as Groovy](https://gist.github.com/dkandalov/5024580) - that's exactly what it does
 - [Code History Mining](https://github.com/dkandalov/code-history-mining) - (not a tiny project) allows to grab, analyze and visualize project source code history
 - [Taskbar Icon Changer](https://gist.github.com/markusmo3/ee46e5fe81d4dacea7110134f4ca953f) - Changes the Windows Taskbar icon depending on the project name to provide a better overview when working with multiple projects
  - [Refocus Pinned Find Window](https://gist.github.com/fc1943s/411540e9e29a1296650bcaa8f9a27eec) - Shortcut to refocus the 'Find in Path' dialog with the Pin option enabled after selecting a result entry


### Similar plugins
The idea of running code inside IntelliJ is not original. 
There are/were similar plugins:
 - [IDE Scripting Console](https://youtrack.jetbrains.com/issue/IDEA-138252) (experimental feature, bundled with IntelliJ since 14.1)
 - [Script Monkey](http://plugins.intellij.net/plugin?pr=idea&pluginId=3674)
 - [PMIP - Poor Mans IDE Plugin](http://plugins.intellij.net/plugin/?idea&pluginId=4571) (no longer available)
 - [Remote Groovy Console](http://plugins.intellij.net/plugin/?id=5373) (most likely out-of-date)
 - [Groovy Console Plugin](http://plugins.intellij.net/plugin?pr=idea&pluginId=4660) (most likely out-of-date)
 - [HotPlugin](http://plugins.intellij.net/plugin?pr=idea&pluginId=1020) (most likely out-of-date)


### Contributing
Please see [CONTRIBUTING.md](https://github.com/dkandalov/live-plugin/blob/master/CONTRIBUTING.md).
