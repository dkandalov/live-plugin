[![Build Status](https://github.com/dkandalov/live-plugin/workflows/CI/badge.svg)](https://github.com/dkandalov/live-plugin/actions)

### LivePlugin
This is a plugin for [IntelliJ](https://github.com/JetBrains/intellij-community) IDEs to create plugins at runtime
using [Groovy](http://groovy.codehaus.org) and [Kotlin](http://kotlinlang.org).
To install search for "LivePlugin" in `IDE Preferences -> Plugins -> Marketplace`.
See also [plugin repository page](http://plugins.jetbrains.com/plugin/7282).

<img src="https://raw.github.com/dkandalov/live-plugin/master/screenshots/live-plugin-demo.gif" alt="demo" title="demo" align="center"/>


### Why?
 - **Minimal setup** — you can edit and execute plugins in any project, i.e. less effort compared to creating a separate project for plugin development.
 - **Fast feedback loop** — plugins are executed in the same JVM instance as IDE, so there is no need to restart
   (this is similar to [dynamic plugins](https://plugins.jetbrains.com/docs/intellij/dynamic-plugins.html)
   except that it works in the same IDE instance).
 - **Usable IDE API** — LivePlugin adds a thin API layer on top of the IntelliJ to highlight some entry points
   and make common tasks easier.


### Examples
Hello world:
```groovy
import static liveplugin.PluginUtil.show
show("Hello world") // shows balloon message with "Hello world" text
```
Insert New Line Above Action:
```groovy
import com.intellij.openapi.actionSystem.AnActionEvent
import static liveplugin.PluginUtil.*

// Action to insert new line above the current line.
// Based on this post http://martinfowler.com/bliki/InternalReprogrammability.html
// Note that there is "Start New Line Before Current" action (ctrl+alt+enter) which does almost the same thing.
registerAction("Insert New Line Above", "alt shift ENTER") { AnActionEvent event ->
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
show("Loaded 'Insert New Line Above' action<br/>Use 'Alt+Shift+Enter' to run it")
```


### How to start writing plugins
Make sure "hello world" works fine:
- In the `Plugins` tool window select "hello-world" plugin and click "Run" button to execute the plugin (`Run Plugin`
  action with `ctrl+shift+L` or `alt+C, alt+E` shortcut). It should display a message.
- Make a small modification in `plugin.groovy`/`plugin.kts` and rerun the plugin. 
  On the second run, previous version of the plugin will be unloaded before the code is evaluated again.
- Modify `plugin.groovy`/`plugin.kts` file again so that it fails to compile/run.
  You should see an error message in the `Run` tool window which, hopefully, makes sense.
- Note that plugins are just folders with `plugin.groovy` or `plugin.kts` scripts as entry points. 
  This means that you can, for example, copy path to the plugin folder using `Copy Path` action (`ctrl/cmd+alt+C` shortcut).

Try bundled examples:
- In the `Plugins` tool window click "Plus" button (`Add Plugin` action) and select 
  Groovy or Kotlin examples. 
- It might be useful to install [Groovy](http://plugins.jetbrains.com/plugin/1524?pr=idea) or 
  [Kotlin](https://plugins.jetbrains.com/plugin/6954-kotlin) plugin if your IDE supports them. 

Take a look at settings in the `Plugins` toowindow:
- `Run Plugins on IDE Start` — to run all plugins on IDE start.
- `Run Project Specific Plugins` — to run all plugins in `.live-plugins` project directory when 
the project is opened and unload them when the project is closed.
- `Add LivePlugin and IDE Jars to Project` — useful for Groovy plugins
to get auto-completion and code navigation in plugin code.
(There is no doubt that adding jars unrelated to your project is a hack 
but there seems to be no major problems with it.) Note that Kotlin plugins should
have auto-completion and code navigation without it.

Learn more about IntelliJ API:
- Read (or at least skim) [plugin development fundamentals](https://plugins.jetbrains.com/docs/intellij/fundamentals.html)
  and the following sections.
- Clone [IntelliJ source code](https://github.com/JetBrains/intellij-community)
or explore it on GitHub or 
[Upsource](https://upsource.jetbrains.com/idea-ce/structure/idea-ce-ba0c8fc9ab9bf23a71a6a963cd84fc89b09b9fc8/).
  One strategy which I like is to search for text you can see in IDE UI and try to figure out 
  how it's connected to the code which does actual work.
- [PluginUtil](https://github.com/dkandalov/live-plugin/blob/master/src/plugin-util-groovy/liveplugin/PluginUtil.groovy) class
  and [liveplugin](https://github.com/dkandalov/live-plugin/tree/master/src/plugin-util-kotlin/liveplugin) package
  might have some good starting points to explore IntelliJ API.

Once your plugin has grown, you can move it to a proper plugin project 
[still using live plugin for reloading](https://github.com/dkandalov/live-plugin/wiki/Liveplugin-as-an-entry-point-for-standard-plugins)
and maybe then convert it to become a [dynamic plugin](https://plugins.jetbrains.com/docs/intellij/dynamic-plugins.html).

If something doesn't work or doesn't make sense, please feel free to ask
in `#live-plugin` channel on [Jetbrains platform slack](https://plugins.jetbrains.com/slack)
or [report an issue](https://github.com/dkandalov/live-plugin/issues) 
(it's ok to report an issue even if it's just a question).


### Practical use cases
 - prototyping of IntelliJ plugins
 - experimenting with IntelliJ API
 - project-specific workflow automation
 - integrating shell scripts with IDE
 

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
This is essentially a layer on top of the standard IntelliJ API. 
If you find yourself writing interesting IDE scripts, feel free to create pull request or send a gist
to include your code into `PluginUtil`. This is experimental API and there is no intention to keep it minimal.
`PluginUtil` is not required though and you can always use IntelliJ classes directly.
  
Also note that:
 - plugins are evaluated with new classloader on each run
 - plugins are stored in `$HOME/.$INTELLIJ_VERSION/config/live-plugins`
(on Mac `$HOME/Library/Application Support/IntelliJIdea15/live-plugins`)
Note that you can use `ctrl+shift+C` shortcut to copy file/folder path.
 - if available, Groovy library bundled with IDE is used


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
